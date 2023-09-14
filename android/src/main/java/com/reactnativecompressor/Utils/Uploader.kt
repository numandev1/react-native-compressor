package com.reactnativecompressor.Utils

import android.util.Log
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableMap
import io.github.lizhangqu.coreprogress.ProgressHelper
import io.github.lizhangqu.coreprogress.ProgressUIListener
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request.Builder
import okhttp3.RequestBody
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.Locale

object Uploader {
    private const val TAG = "asyncTaskUploader"

    fun upload(fileUrl: String, _options: ReadableMap?, reactContext: ReactApplicationContext, promise: Promise) {
        val options = _options?.let { UploaderHelper.fromMap(it) }
        val uploadableFile = File(fileUrl)
        val url = options?.url
        var contentType: String? = "video"
        val okHttpClient = OkHttpClient()
        val builder = Builder()
      if (url != null) {
        builder.url(url)
      }
        val headerIterator = options?.headers?.keySetIterator()
        while (headerIterator?.hasNextKey() == true) {
          val key = headerIterator.nextKey()
          val value = options.headers?.getString(key)
          Log.d(TAG, "$key  value: $value")
          builder.addHeader(key, value.toString())
          if (key.lowercase(Locale.getDefault()) == "content-type:") {
            contentType = value
          }
        }
        val mediaType: MediaType? = contentType?.toMediaTypeOrNull();
        val body = RequestBody.create(mediaType, uploadableFile)
        val requestBody = ProgressHelper.withProgress(body, object : ProgressUIListener() {
            //if you don't need this method, don't override this methd. It isn't an abstract method, just an empty method.
            override fun onUIProgressStart(totalBytes: Long) {
                super.onUIProgressStart(totalBytes)
                Log.d(TAG, "onUIProgressStart:$totalBytes")
            }

            override fun onUIProgressChanged(numBytes: Long, totalBytes: Long, percent: Float, speed: Float) {
                EventEmitterHandler.sendUploadProgressEvent(numBytes,totalBytes,options?.uuid)
                Log.d(TAG, "=============start===============")
                Log.d(TAG, "numBytes:$numBytes")
                Log.d(TAG, "totalBytes:$totalBytes")
                Log.d(TAG, "percent:$percent")
                Log.d(TAG, "speed:$speed")
                Log.d(TAG, "============= end ===============")
            }

            //if you don't need this method, don't override this methd. It isn't an abstract method, just an empty method.
            override fun onUIProgressFinish() {
                super.onUIProgressFinish()
                Log.d(TAG, "onUIProgressFinish:")
            }
        })
        builder.put(requestBody)
        val call = okHttpClient.newCall(builder.build())
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.d(TAG, "=============onFailure===============")
                promise.reject("")
                e.printStackTrace()
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                Log.d(TAG, "=============onResponse===============")
                Log.d(TAG, "request headers:" + response.request.headers)
                Log.d(TAG, "response code:" + response.code)
                Log.d(TAG, "response headers:" + response.headers)
                Log.d(TAG, "response body:" + response.body!!.string())
                val param = Arguments.createMap()
                param.putInt("status", response.code)
                promise.resolve(param)
            }
        })
    }
}


class UploaderHelper {
  var uuid: String? = null
  var method: String? = null
  var headers: ReadableMap? = null
  var url: String? = null

  companion object {
    fun fromMap(map: ReadableMap): UploaderHelper {
      val options = UploaderHelper()
      val iterator = map.keySetIterator()
      while (iterator.hasNextKey()) {
        val key = iterator.nextKey()
        when (key) {
          "uuid" -> options.uuid = map.getString(key)
          "method" -> options.method = map.getString(key)
          "headers" -> options.headers = map.getMap(key)
          "url" -> options.url = map.getString(key)
        }
      }
      return options
    }
  }
}
