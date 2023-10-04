package com.reactnativecompressor.Utils

import com.facebook.react.bridge.ReadableMap
import okhttp3.RequestBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.IOException
import okio.Sink
import okio.buffer

interface Record
annotation class Field(val key: String = "")
interface Enumerable

@FunctionalInterface
fun interface RequestBodyDecorator {
  fun decorate(requestBody: RequestBody): RequestBody
}
enum class EncodingType(val value: String) : Enumerable {
  UTF8("utf8"),
  BASE64("base64")
}

enum class UploadType(val value: Int) : Enumerable {
  BINARY_CONTENT(0),
  MULTIPART(1)
}
data class UploaderOptions(
  val headers: Map<String, String>?,
  val httpMethod: HttpMethod = HttpMethod.POST,
  val uploadType: UploadType,
  val fieldName: String?,
  val mimeType: String?,
  val parameters: Map<String, String>?,
  val uuid:String,
  val url:String
) : Record

enum class HttpMethod(val value: String) : Enumerable {
  POST("POST"),
  PUT("PUT"),
  PATCH("PATCH")
}

internal class UploaderOkHttpNullException :
  CodedException("okHttpClient is null")
internal class CookieHandlerNotFoundException :
  CodedException("Failed to find CookieHandler")
interface CodedThrowable {
  val code: String?
  val message: String?
}
abstract class CodedException : Exception, CodedThrowable {
  constructor(message: String?) : super(message)
  constructor(cause: Throwable?) : super(cause)
  constructor(message: String?, cause: Throwable?) : super(message, cause)

  override val code: String
    get() = "ERR_UNSPECIFIED_ANDROID_EXCEPTION"
}

@FunctionalInterface
interface CountingRequestListener {
  fun onProgress(bytesWritten: Long, contentLength: Long)
}

@FunctionalInterface


private class CountingSink(
  sink: Sink,
  private val requestBody: RequestBody,
  private val progressListener: CountingRequestListener
) : ForwardingSink(sink) {
  private var bytesWritten = 0L

  override fun write(source: Buffer, byteCount: Long) {
    super.write(source, byteCount)

    bytesWritten += byteCount
    progressListener.onProgress(bytesWritten, requestBody.contentLength())
  }
}

class CountingRequestBody(
  private val requestBody: RequestBody,
  private val progressListener: CountingRequestListener
) : RequestBody() {
  override fun contentType() = requestBody.contentType()

  @Throws(IOException::class)
  override fun contentLength() = requestBody.contentLength()

  override fun writeTo(sink: BufferedSink) {
    val countingSink = CountingSink(sink, this, progressListener)

    val bufferedSink = countingSink.buffer()
    requestBody.writeTo(bufferedSink)
    bufferedSink.flush()
  }
}


fun convertReadableMapToUploaderOptions(options: ReadableMap): UploaderOptions {
  val headers = options.getMap("headers")?.toHashMap() as? Map<String, String>
  val httpMethod = HttpMethod.valueOf(options.getString("httpMethod") ?: "POST")
  val uploadTypeInt = options.getInt("uploadType")
  val uploadType = when (uploadTypeInt) {
    UploadType.BINARY_CONTENT.value -> UploadType.BINARY_CONTENT
    UploadType.MULTIPART.value -> UploadType.MULTIPART
    else -> UploadType.BINARY_CONTENT // Provide a default value or handle the case as needed
  }
  val fieldName = options.getString("fieldName")?: "file"
  val mimeType = options.getString("mimeType")?: ""
  val parameters = options.getMap("parameters")?.toHashMap() as? Map<String, String>
  val uuid = options.getString("uuid") ?: ""
  val url = options.getString("url") ?: ""

  return UploaderOptions(headers, httpMethod, uploadType, fieldName, mimeType, parameters, uuid, url)
}
