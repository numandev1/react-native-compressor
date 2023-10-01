package com.reactnativecompressor.Audio

import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableMap
import com.reactnativecompressor.Utils.Utils

class AudioMain(private val reactContext: ReactApplicationContext) {
  fun compress_audio(
    fileUrl: String,
    optionMap: ReadableMap,
    promise: Promise) {
    try {
      val options = AudioHelper.fromMap(optionMap)
      val quality = options.quality
      val realPath = Utils.getRealPath(fileUrl, reactContext)
      Utils.addLog(fileUrl + "\n realPath= " + realPath)
      AudioCompressor.CompressAudio(realPath!!, quality!!,reactContext,promise)
    } catch (ex: Exception) {
      promise.reject(ex)
    }
  }
}
