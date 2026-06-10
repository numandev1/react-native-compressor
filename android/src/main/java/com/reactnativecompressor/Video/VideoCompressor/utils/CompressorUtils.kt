package com.reactnativecompressor.Video.VideoCompressor.utils

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.util.Log
import com.reactnativecompressor.Video.VideoCompressor.video.Mp4Movie
import java.io.File

object CompressorUtils {

  // Minimum height and width for videos
  private const val MIN_HEIGHT = 640.0
  private const val MIN_WIDTH = 368.0

  // Interval between I-frames (keyframes) in seconds
  private const val I_FRAME_INTERVAL = 1

  /**
   * Get the width of the video from metadata or use a default value if not available.
   */
  fun prepareVideoWidth(
    mediaMetadataRetriever: MediaMetadataRetriever,
  ): Double {
    val widthData =
      mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
    return if (widthData.isNullOrEmpty()) {
      MIN_WIDTH
    } else {
      widthData.toDouble()
    }
  }

  /**
   * Get the height of the video from metadata or use a default value if not available.
   */
  fun prepareVideoHeight(
    mediaMetadataRetriever: MediaMetadataRetriever,
  ): Double {
    val heightData =
      mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
    return if (heightData.isNullOrEmpty()) {
      MIN_HEIGHT
    } else {
      heightData.toDouble()
    }
  }

  /**
   * Set up an Mp4Movie with rotation, cache file and optional ISO 6709
   * location string forwarded from the source video.
   */
  fun setUpMP4Movie(
    rotation: Int,
    cacheFile: File,
    location: String? = null,
  ): Mp4Movie {
    val movie = Mp4Movie()
    movie.apply {
      setCacheFile(cacheFile)
      setRotation(rotation)
      setLocation(location)
    }
    return movie
  }

  /**
   * Set output parameters like bitrate and frame rate for video encoding.
   */
  fun setOutputFileParameters(
    inputFormat: MediaFormat,
    outputFormat: MediaFormat,
    newBitrate: Int,
    targetFrameRate: Int,
    applyThroughputTuning: Boolean = true,
  ) {
    val newFrameRate = targetFrameRate.coerceAtLeast(1)
    val iFrameInterval = getIFrameIntervalRate(inputFormat)
    outputFormat.apply {
      setInteger(
        MediaFormat.KEY_COLOR_FORMAT,
        MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
      )

      setInteger(MediaFormat.KEY_FRAME_RATE, newFrameRate)
      setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, iFrameInterval)
      // Bitrate in bits per second
      setInteger(MediaFormat.KEY_BIT_RATE, newBitrate)

      // Throughput tuning. Some encoders reject these keys at configure() time,
      // so the caller drops them (applyThroughputTuning = false) and reconfigures
      // with default rate control on a fallback pass — see Compressor.prepareEncoder.
      if (applyThroughputTuning) {
        // VBR transcodes ~10-20% faster than CBR by skipping rate-control overhead
        // on low-motion frames; quality stays equivalent for short-form video.
        setInteger(
          MediaFormat.KEY_BITRATE_MODE,
          MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR
        )
        // Hint the hardware codec to run as fast as it can (not throttled to
        // realtime playback) and at the highest scheduling priority. These keys
        // unlock full throughput on Qualcomm / Exynos / MTK SoCs that accept them.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
          setInteger(MediaFormat.KEY_PRIORITY, 0)
          setInteger(MediaFormat.KEY_OPERATING_RATE, Short.MAX_VALUE.toInt())
        }
      }

      getColorStandard(inputFormat)?.let {
        setInteger(MediaFormat.KEY_COLOR_STANDARD, it)
      }

      getColorTransfer(inputFormat)?.let {
        setInteger(MediaFormat.KEY_COLOR_TRANSFER, it)
      }

      getColorRange(inputFormat)?.let {
        setInteger(MediaFormat.KEY_COLOR_RANGE, it)
      }

      Log.i(
        "Output file parameters",
        "videoFormat: $this"
      )
    }
  }

  // Get the I-frame (keyframe) interval from the input format or use a default value
  private fun getIFrameIntervalRate(format: MediaFormat): Int {
    return if (format.containsKey(MediaFormat.KEY_I_FRAME_INTERVAL)) format.getInteger(
      MediaFormat.KEY_I_FRAME_INTERVAL
    )
    else I_FRAME_INTERVAL
  }

  // Get the color standard from the input format or null if not available
  private fun getColorStandard(format: MediaFormat): Int? {
    return if (format.containsKey(MediaFormat.KEY_COLOR_STANDARD)) format.getInteger(
      MediaFormat.KEY_COLOR_STANDARD
    )
    else null
  }

  // Get the color transfer from the input format or null if not available
  private fun getColorTransfer(format: MediaFormat): Int? {
    return if (format.containsKey(MediaFormat.KEY_COLOR_TRANSFER)) format.getInteger(
      MediaFormat.KEY_COLOR_TRANSFER
    )
    else null
  }

  // Get the color range from the input format or null if not available
  private fun getColorRange(format: MediaFormat): Int? {
    return if (format.containsKey(MediaFormat.KEY_COLOR_RANGE)) format.getInteger(
      MediaFormat.KEY_COLOR_RANGE
    )
    else null
  }

  /**
   * Find the track index for video or audio in the media extractor.
   *
   * @param extractor MediaExtractor used to extract data from the media source.
   * @param isVideo Determines whether we are looking for a video or audio track.
   * @return Index of the requested track, or -5 if not found.
   */
  fun findTrack(
    extractor: MediaExtractor,
    isVideo: Boolean,
  ): Int {
    val numTracks = extractor.trackCount
    for (i in 0 until numTracks) {
      val format = extractor.getTrackFormat(i)
      val mime = format.getString(MediaFormat.KEY_MIME)
      if (isVideo) {
        if (mime?.startsWith("video/")!!) return i
      } else {
        if (mime?.startsWith("audio/")!!) return i
      }
    }
    return -5
  }

  /**
   * Log an exception with a meaningful message.
   */
  fun printException(exception: Throwable) {
    var message = "An error has occurred!"
    exception.localizedMessage?.let {
      message = it
    }
    Log.e("Compressor", message, exception)
  }

  /**
   * Check if the device has QTI (Qualcomm Technologies, Inc.) codecs.
   */
  fun hasQTI(): Boolean {
    val list = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
    for (codec in list) {
      Log.i("CODECS: ", codec.name)
      if (codec.name.contains("qti.avc")) {
        return true
      }
    }
    return false
  }

  // MIME type reported by MediaExtractor for Dolby Vision tracks (e.g. iPhone .MOV files)
  private const val MIMETYPE_VIDEO_DOLBY_VISION = "video/dolby-vision"

  /**
   * Check whether the device exposes a decoder for the given MIME type.
   */
  private fun hasDecoderForMime(mime: String): Boolean {
    val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
    for (codec in codecList.codecInfos) {
      if (codec.isEncoder) continue
      for (type in codec.supportedTypes) {
        if (type.equals(mime, ignoreCase = true)) return true
      }
    }
    return false
  }

  /**
   * Resolve the backward-compatible base-layer MIME type for a Dolby Vision track.
   *
   * Dolby Vision profiles 8.x (DvheSt) and 4 (DvheDtr) carry an HEVC base layer, and
   * profile 9 (DvavSe) carries an AVC base layer; these can be decoded by the standard
   * HEVC/AVC decoders. Profiles 5 (DvheStn) and 7 (DvheDtb) have no usable single base
   * layer, so they return null. When the profile is unknown we assume HEVC, which covers
   * the common consumer case (e.g. iPhone records Dolby Vision profile 8).
   */
  private fun dolbyVisionBaseLayerMime(inputFormat: MediaFormat): String? {
    if (!inputFormat.containsKey(MediaFormat.KEY_PROFILE)) {
      return MediaFormat.MIMETYPE_VIDEO_HEVC
    }
    return when (inputFormat.getInteger(MediaFormat.KEY_PROFILE)) {
      MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvavSe -> MediaFormat.MIMETYPE_VIDEO_AVC
      MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvheSt,
      MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvheDtr -> MediaFormat.MIMETYPE_VIDEO_HEVC
      else -> null
    }
  }

  /**
   * Ensure the input video format can be decoded on this device.
   *
   * Some containers (notably iPhone `.MOV` files) expose the video track as
   * `video/dolby-vision`. Many Android devices have no Dolby Vision decoder, so
   * `MediaCodec.createDecoderByType("video/dolby-vision")` fails with NAME_NOT_FOUND.
   * When the dedicated decoder is missing but the stream carries a backward-compatible
   * base layer, this rewrites the format MIME to the base-layer codec so the standard
   * HEVC/AVC decoder can decode it. If no compatible decoder exists, it throws a clear
   * error instead of letting the cryptic native failure surface (see issue #398).
   */
  fun ensureDecodableVideoFormat(inputFormat: MediaFormat) {
    val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: return
    if (hasDecoderForMime(mime)) return

    if (mime.equals(MIMETYPE_VIDEO_DOLBY_VISION, ignoreCase = true)) {
      val fallbackMime = dolbyVisionBaseLayerMime(inputFormat)
      if (fallbackMime != null && hasDecoderForMime(fallbackMime)) {
        Log.w(
          "Compressor",
          "No Dolby Vision decoder on this device; decoding the $fallbackMime base layer instead."
        )
        inputFormat.setString(MediaFormat.KEY_MIME, fallbackMime)
        return
      }
      throw IllegalStateException(
        "This video uses Dolby Vision, which is not supported by this device's decoders " +
          "and has no backward-compatible base layer to fall back to."
      )
    }

    throw IllegalStateException("No decoder available for video format: $mime")
  }
}
