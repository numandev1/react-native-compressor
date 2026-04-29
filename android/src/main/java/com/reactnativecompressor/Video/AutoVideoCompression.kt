package com.reactnativecompressor.Video

import android.media.MediaMetadataRetriever
import android.net.Uri
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.reactnativecompressor.Utils.Utils.compressVideo
import com.reactnativecompressor.Utils.Utils.generateCacheFilePath
import java.io.File

object AutoVideoCompression {
    fun createCompressionSettings(fileUrl: String?, options: VideoCompressorHelper, promise: Promise, reactContext: ReactApplicationContext?) {
        val minimumFileSizeForCompress = options.minimumFileSizeForCompress
        try {
            val uri = Uri.parse(fileUrl)
            val srcPath = uri.path
            val metaRetriever = MediaMetadataRetriever()
            metaRetriever.setDataSource(srcPath)
            val file = File(srcPath)
            val sizeInBytes = file.length().toFloat()
            val sizeInMb = sizeInBytes / (1024 * 1024)
            if (sizeInMb > minimumFileSizeForCompress) {
                val destinationPath = generateCacheFilePath("mp4", reactContext!!)
                val actualHeight = VideoCompressorHelper.getMetadataInt(metaRetriever, MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                val actualWidth = VideoCompressorHelper.getMetadataInt(metaRetriever, MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                val bitrate = VideoCompressorHelper.getMetadataInt(metaRetriever, MediaMetadataRetriever.METADATA_KEY_BITRATE)
                val frameRate = VideoCompressorHelper.getMetadataInt(metaRetriever, MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                if (actualHeight <= 0 || actualWidth <= 0) {
                    promise.reject(Throwable("Failed to read the input video dimensions"))
                    return
                }
                val profile = VideoCompressionProfileFactory.createAuto(
                    sourceWidth = actualWidth,
                    sourceHeight = actualHeight,
                    sourceBitrate = bitrate,
                    sourceFrameRate = frameRate,
                    maxSize = options.maxSize,
                )
                compressVideo(
                    srcPath!!,
                    destinationPath,
                    profile.width,
                    profile.height,
                    profile.bitrate.toFloat(),
                    profile.frameRate,
                    options.uuid!!,
                    options.progressDivider!!,
                    promise,
                    reactContext,
                )
            } else {
                promise.resolve(fileUrl)
            }
        } catch (ex: Exception) {
            promise.reject(ex)
        }
    }
}
