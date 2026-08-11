package com.reactnativecompressor.Video

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableMap
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
    var metaRetriever: MediaMetadataRetriever? = null
    try {
      filePath = Utils.getRealPath(filePath, reactContext)
      val uri = Uri.parse(filePath)
      val srcPath = uri.path
      metaRetriever = MediaMetadataRetriever()
      metaRetriever.setDataSource(srcPath)
      val file = File(srcPath)
      val extension = filePath!!.substring(filePath.lastIndexOf(".") + 1)

      val params = Arguments.createMap()
      params.putString("extension", extension)
      params.putString("fileName", file.name)
      params.putDouble("size", file.length().toDouble())
      // METADATA_KEY_DURATION is milliseconds; the JS contract is seconds.
      params.putDouble("duration", (metaRetriever.intOf(MediaMetadataRetriever.METADATA_KEY_DURATION) ?: 0) / 1000.0)

      val hasVideo = metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes"
      val hasAudio = metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
      params.putBoolean("hasVideo", hasVideo)
      params.putBoolean("hasAudio", hasAudio)

      val width = metaRetriever.intOf(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH) ?: 0
      val height = metaRetriever.intOf(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT) ?: 0
      val rotation = normalizeRotation(metaRetriever.intOf(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION) ?: 0)
      val swapsAxes = rotation == 90 || rotation == 270
      params.putInt("width", width)
      params.putInt("height", height)
      params.putInt("displayWidth", if (swapsAxes) height else width)
      params.putInt("displayHeight", if (swapsAxes) width else height)
      params.putInt("rotation", rotation)

      metaRetriever.intOf(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.let { params.putInt("bitrate", it) }
      metaRetriever.intOf(MediaMetadataRetriever.METADATA_KEY_NUM_TRACKS)?.let { params.putInt("trackCount", it) }

      val location = metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)
      if (location != null) {
        params.putString("location", location)
        parseIso6709(location)?.let { (latitude, longitude) ->
          params.putDouble("latitude", latitude)
          params.putDouble("longitude", longitude)
        }
      }

      // Track-level details: format, codec, colour transfer and audio parameters. Failing to read
      // them must not lose everything gathered above.
      try {
        readTrackDetails(srcPath, params, hasVideo)
      } catch (e: Exception) {
        Log.w("getVideoMetaData", "Failed to read track details", e)
      }
      if (!params.hasKey("isHDR")) {
        params.putBoolean("isHDR", false)
        params.putString("colorTransfer", "sdr")
      }

      promise.resolve(params)
    } catch (e: Exception) {
      promise.reject(e)
    } finally {
      try {
        metaRetriever?.release()
      } catch (e: Exception) {
        Log.w("getVideoMetaData", "Failed to release MediaMetadataRetriever", e)
      }
    }
  }

  /**
   * Reads per-track details the retriever does not expose: the video codec and colour transfer, the
   * frame rate/count, and the audio track's bitrate, sample rate and channel count.
   */
  private fun readTrackDetails(srcPath: String?, params: WritableMap, hasVideo: Boolean) {
    val extractor = MediaExtractor()
    try {
      extractor.setDataSource(srcPath!!)
      for (index in 0 until extractor.trackCount) {
        val format = extractor.getTrackFormat(index)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
        when {
          hasVideo && mime.startsWith("video/") && !params.hasKey("codec") -> {
            params.putString("codec", normalizeCodec(mime))
            format.intOrNull(MediaFormat.KEY_FRAME_RATE)?.let { params.putInt("frameRate", it) }
            format.intOrNull(MediaFormat.KEY_BIT_RATE)?.let { params.putInt("bitrate", it) }
            // KEY_FRAME_COUNT is API 28+; absent on older platforms and on some containers.
            format.intOrNull("frame-count")?.let { params.putInt("frameCount", it) }
            val transfer = colorTransferName(format.intOrNull(MediaFormat.KEY_COLOR_TRANSFER))
            params.putString("colorTransfer", transfer)
            params.putBoolean("isHDR", transfer == "hlg" || transfer == "pq")
          }
          mime.startsWith("audio/") && !params.hasKey("audioSampleRate") -> {
            format.intOrNull(MediaFormat.KEY_BIT_RATE)?.let { params.putInt("audioBitrate", it) }
            format.intOrNull(MediaFormat.KEY_SAMPLE_RATE)?.let { params.putInt("audioSampleRate", it) }
            format.intOrNull(MediaFormat.KEY_CHANNEL_COUNT)?.let { params.putInt("audioChannels", it) }
          }
        }
      }
    } finally {
      extractor.release()
    }
  }

  private fun MediaMetadataRetriever.intOf(key: Int): Int? = extractMetadata(key)?.toIntOrNull()

  private fun MediaFormat.intOrNull(key: String): Int? = if (containsKey(key)) {
    try {
      getInteger(key)
    } catch (e: ClassCastException) {
      // Some devices store these as floats (notably frame rate).
      getFloat(key).toInt()
    }
  } else {
    null
  }

  companion object {
    /** Snaps an arbitrary degree value to the nearest quarter turn in `0, 90, 180, 270`. */
    private fun normalizeRotation(degrees: Int): Int {
      val quarters = Math.round(degrees / 90.0).toInt()
      return ((quarters % 4) + 4) % 4 * 90
    }

    private fun normalizeCodec(mime: String): String = when (mime) {
      MediaFormat.MIMETYPE_VIDEO_AVC -> "h264"
      MediaFormat.MIMETYPE_VIDEO_HEVC, "video/dolby-vision" -> "hevc"
      MediaFormat.MIMETYPE_VIDEO_AV1 -> "av1"
      MediaFormat.MIMETYPE_VIDEO_VP9 -> "vp9"
      MediaFormat.MIMETYPE_VIDEO_VP8 -> "vp8"
      MediaFormat.MIMETYPE_VIDEO_MPEG4 -> "mpeg4"
      else -> mime.removePrefix("video/").lowercase()
    }

    private fun colorTransferName(colorTransfer: Int?): String = when (colorTransfer) {
      MediaFormat.COLOR_TRANSFER_HLG -> "hlg"
      MediaFormat.COLOR_TRANSFER_ST2084 -> "pq"
      MediaFormat.COLOR_TRANSFER_LINEAR -> "linear"
      else -> "sdr"
    }

    /**
     * Parses the ISO-6709 string `MediaMetadataRetriever` returns (`"+37.5090-122.2620/"`) into
     * signed decimal degrees. Returns null when the string is not in that form.
     */
    private fun parseIso6709(location: String): Pair<Double, Double>? {
      val match = Regex("([+-]\\d+(?:\\.\\d+)?)([+-]\\d+(?:\\.\\d+)?)").find(location) ?: return null
      val latitude = match.groupValues[1].toDoubleOrNull() ?: return null
      val longitude = match.groupValues[2].toDoubleOrNull() ?: return null
      return latitude to longitude
    }
  }
}
