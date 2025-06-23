package com.reactnativecompressor.Utils

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableMap
import com.reactnativecompressor.Utils.Utils.slashifyFilePath
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.net.URLConnection
import java.util.concurrent.TimeUnit

class Uploader(private val reactContext: ReactApplicationContext) {
  val TAG = "asyncTaskUploader"
  var client: OkHttpClient? = null
  val MIN_EVENT_DT_MS: Long = 100
  val httpCallManager = HttpCallManager()

  fun upload(
    fileUriString: String,
    _options: ReadableMap,
    reactContext: ReactApplicationContext,
    promise: Promise
  ) {
    val options: UploaderOptions = convertReadableMapToUploaderOptions(_options)
    val url = options.url
    val uuid = options.uuid
    val progressListener: CountingRequestListener = object : CountingRequestListener {
      private var mLastUpdate: Long = -1
      override fun onProgress(bytesWritten: Long, contentLength: Long) {
        val currentTime = System.currentTimeMillis()

        // Throttle events. Sending too many events will block the JS event loop.
        // Make sure to send the last event when we're at 100%.
        if (currentTime > mLastUpdate + MIN_EVENT_DT_MS || bytesWritten == contentLength) {
          mLastUpdate = currentTime
          EventEmitterHandler.sendUploadProgressEvent(bytesWritten, contentLength, uuid)
        }
      }
    }
    val request = createUploadRequest(
      url, fileUriString, options
    ) { requestBody -> CountingRequestBody(requestBody, progressListener) }

    okHttpClient?.let {
      val call = it.newCall(request)
      httpCallManager.registerTask(call,uuid)
      call.enqueue(
        object : Callback {
          override fun onFailure(call: Call, e: IOException) {
            Log.e(TAG, e.message.toString())
            promise.reject(TAG, e.message)
          }

          override fun onResponse(call: Call, response: Response) {
            val param = Arguments.createMap()
            param.putInt("status", response.code)
            param.putString("body", response.body?.string())
            param.putMap("headers", translateHeaders(response.headers))
            response.close()
            promise.resolve(param)
          }
        })
    } ?: run {
      promise.reject(UploaderOkHttpNullException())
    }

  }

  @get:Synchronized
  private val okHttpClient: OkHttpClient?
    get() {
      if (client == null) {
        val builder = OkHttpClient.Builder()
          .connectTimeout(60, TimeUnit.SECONDS)
          .readTimeout(60, TimeUnit.SECONDS)
          .writeTimeout(60, TimeUnit.SECONDS)
        client = builder.build()
      }
      return client
    }

  @Throws(IOException::class)
  private fun createUploadRequest(
    url: String,
    fileUriString: String,
    options: UploaderOptions,
    decorator: RequestBodyDecorator
  ): Request {
    val fileUri = Uri.parse(slashifyFilePath(fileUriString))
    fileUri.checkIfFileExists()

    val requestBuilder = Request.Builder().url(url)
    options.headers?.let {
      it.forEach { (key, value) -> requestBuilder.addHeader(key, value) }
    }

    val body = createRequestBody(options, decorator, fileUri.toFile())
    return options.httpMethod.let { requestBuilder.method(it.value, body).build() }
  }

  @SuppressLint("NewApi")
  private fun createRequestBody(
    options: UploaderOptions,
    decorator: RequestBodyDecorator,
    file: File
  ): RequestBody {
    return when (options.uploadType) {
      UploadType.BINARY_CONTENT -> {
        val mimeType: String? = if (options.mimeType?.isNotEmpty() == true) {
          options.mimeType
        } else {
          getContentType(reactContext, file) ?: "application/octet-stream"
        }
        val contentType = mimeType?.toMediaTypeOrNull()
        decorator.decorate(file.asRequestBody(contentType))
      }

      UploadType.MULTIPART -> {
        val bodyBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
        options.parameters?.let {
          (it as Map<String, Any>)
            .forEach { (key, value) -> bodyBuilder.addFormDataPart(key, value.toString()) }
        }
        val mimeType: String = options.mimeType ?: URLConnection.guessContentTypeFromName(file.name)

        val fieldName = options.fieldName ?: file.name
        bodyBuilder.addFormDataPart(
          fieldName,
          file.name,
          decorator.decorate(file.asRequestBody(mimeType.toMediaTypeOrNull()))
        )
        bodyBuilder.build()
      }
    }
  }

  fun getContentType(context: ReactApplicationContext, file: File): String? {
    val contentResolver: ContentResolver = context.contentResolver
    val fileUri = Uri.fromFile(file)

    // Try to get the MIME type from the ContentResolver
    val mimeType = contentResolver.getType(fileUri)

    // If the ContentResolver couldn't determine the MIME type, try to infer it from the file extension
    if (mimeType == null) {
      val fileExtension = MimeTypeMap.getFileExtensionFromUrl(fileUri.toString())
      if (fileExtension != null) {
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension.lowercase())
      }
    }

    return mimeType
  }

  @Throws(IOException::class)
  private fun Uri.checkIfFileExists() {
    val file = this.toFile()
    if (!file.exists()) {
      throw IOException("Directory for '${file.path}' doesn't exist.")
    }
  }

  // extension functions of Uri class
  private fun Uri.toFile() = if (this.path != null) {
    File(this.path!!)
  } else {
    throw IOException("Invalid Uri: $this")
  }

  private fun translateHeaders(headers: Headers): ReadableMap {
    val responseHeaders = Arguments.createMap()
    for (i in 0 until headers.size) {
      val headerName = headers.name(i)
      // multiple values for the same header
      if (responseHeaders.hasKey(headerName)) {
        val existingValue = responseHeaders.getString(headerName)
        responseHeaders.putString(headerName, "$existingValue, ${headers.value(i)}")
      } else {
        responseHeaders.putString(headerName, headers.value(i))
      }
    }
    return responseHeaders
  }

  fun cancelUpload(uuid:String,shouldCancelAll:Boolean) {
    if(shouldCancelAll)
    {
      httpCallManager.cancelAllTasks()
    }
    else if(uuid=="")
    {
      httpCallManager.taskPop()?.cancel()
    }
    else
    {
      httpCallManager.uploadTaskForId(uuid)?.cancel()
    }
  }
}
