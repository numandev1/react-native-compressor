package com.reactnativecompressor.Audio

import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableMap

// Built instead of the LAME-based AudioCompressor when Compressor_enableAudio=false.
class AudioCompressor {
  companion object {
    val TAG = "AudioMain"
    private const val ERROR_CODE = "E_AUDIO_DISABLED"
    private const val ERROR_MESSAGE =
      "[E_AUDIO_DISABLED] Audio compression is disabled in this build (Compressor_enableAudio=false)."

    @JvmStatic
    fun CompressAudio(
      fileUrl: String,
      optionMap: ReadableMap,
      context: ReactApplicationContext,
      promise: Promise,
    ) {
      promise.reject(ERROR_CODE, ERROR_MESSAGE)
    }
  }
}
