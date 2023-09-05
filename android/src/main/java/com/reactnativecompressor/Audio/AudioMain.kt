package com.reactnativecompressor.Audio

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableMap
import com.reactnativecompressor.Utils.Utils
import com.reactnativecompressor.Video.VideoCompressorHelper

class AudioMain(private val reactContext: ReactApplicationContext) {
  fun compress_audio(
    fileUrl: String,
    optionMap: ReadableMap,
    promise: Promise) {
    try {
      val options = VideoCompressorHelper.fromMap(optionMap)
      val uri = Uri.parse(fileUrl)
      val srcPath = uri.path
      val destinationPath = Utils.generateCacheFilePath("mp3", reactContext)
      val metaRetriever = MediaMetadataRetriever()
      metaRetriever.setDataSource(srcPath)
      val bitrate = options.bitrate
      Log.d("nomi onStart", destinationPath + "onProgress: " + bitrate)
      AudioCompressor().CompressAudio(srcPath, destinationPath, bitrate.toInt() * 1000)
    } catch (ex: Exception) {
      promise.reject(ex)
    }
  }
}
