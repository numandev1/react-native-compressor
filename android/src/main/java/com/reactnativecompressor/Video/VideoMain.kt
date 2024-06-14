package com.reactnativecompressor.Video

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableMap
import com.reactnativecompressor.Utils.Utils
import com.reactnativecompressor.Video.VideoCompressorHelper.Companion.video_activateBackgroundTask_helper
import com.reactnativecompressor.Video.VideoCompressorHelper.Companion.video_deactivateBackgroundTask_helper
import java.io.File

class VideoMain(private val reactContext: ReactApplicationContext) {
    //Video
    fun compress(
            fileUrl: String,
            optionMap: ReadableMap,
            promise: Promise) {
        var fileUrl: String? = fileUrl
        val options = VideoCompressorHelper.fromMap(optionMap)
        fileUrl = Utils.getRealPath(fileUrl, reactContext, options.uuid,options.progressDivider)
        if (options.compressionMethod === VideoCompressorHelper.CompressionMethod.auto) {
            VideoCompressorHelper.VideoCompressAuto(fileUrl, options, promise, reactContext)
        } else {
            VideoCompressorHelper.VideoCompressManual(fileUrl, options, promise, reactContext)
        }
    }

    fun cancelCompression(
            uuid: String) {
        Utils.cancelCompressionHelper(uuid)
        Log.d("cancelCompression", uuid)
    }

     fun activateBackgroundTask(
            options: ReadableMap,
            promise: Promise) {
        try {
            val response: String = video_activateBackgroundTask_helper(options, reactContext)
            promise.resolve(response)
        } catch (ex: Exception) {
            promise.reject(ex)
        }
    }

   fun deactivateBackgroundTask(
            options: ReadableMap,
            promise: Promise) {
        try {
            val response: String = video_deactivateBackgroundTask_helper(options, reactContext)
            promise.resolve(response)
        } catch (ex: Exception) {
            promise.reject(ex)
        }
    }

  fun getVideoMetaData(filePath: String, promise: Promise) {
    var filePath: String? = filePath
    try {
      filePath = Utils.getRealPath(filePath, reactContext)
      val uri = Uri.parse(filePath)
      val srcPath = uri.path
      val metaRetriever = MediaMetadataRetriever()
      metaRetriever.setDataSource(srcPath)
      val file = File(srcPath)
      val sizeInBytes = (file.length()).toDouble()
      val actualHeight = metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)!!.toInt()
      val actualWidth = metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)!!.toInt()
      val duration = metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toDouble()
      val creationTime = metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
      val extension = filePath!!.substring(filePath.lastIndexOf(".") + 1)
      val params = Arguments.createMap()
      params.putDouble("size", sizeInBytes)
      params.putInt("width", actualWidth)
      params.putInt("height", actualHeight)
      params.putDouble("duration", duration / 1000)
      params.putString("extension", extension)
      params.putString("creationTime", creationTime.toString())
      promise.resolve(params)
    } catch (e: Exception) {
      promise.reject(e)
    }
  }
}
