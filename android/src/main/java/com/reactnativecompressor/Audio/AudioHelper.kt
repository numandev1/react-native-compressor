package com.reactnativecompressor.Audio

import android.media.MediaExtractor
import android.media.MediaFormat
import com.facebook.react.bridge.ReadableMap
import com.reactnativecompressor.Utils.Utils
import java.io.File
import java.io.IOException


class AudioHelper {

  var quality: String? = "medium"
  var bitrate: Int = -1
  var samplerate: Int = -1
  var channels: Int = -1
  var progressDivider: Int? = 0

  companion object {
    fun fromMap(map: ReadableMap): AudioHelper {
      val options = AudioHelper()
      val iterator = map.keySetIterator()
      while (iterator.hasNextKey()) {
        val key = iterator.nextKey()
        when (key) {
          "quality" -> options.quality = map.getString(key)
          "bitrate" -> {
            val bitrate = map.getInt(key)
            options.bitrate = if (bitrate > 320000 || bitrate < 64000) 64000 else bitrate
          }
          "samplerate" -> options.samplerate = map.getInt(key)
          "channels" -> options.channels = map.getInt(key)
        }
      }
      return options
    }


    fun getAudioBitrate(path: String): Int {
      val file = File(path)
      val fileSize = file.length() * 8 // size in bits

      val mex = MediaExtractor()
      try {
        mex.setDataSource(path)
      } catch (e: IOException) {
        e.printStackTrace()
      }

      val mf = mex.getTrackFormat(0)
      val durationUs = mf.getLong(MediaFormat.KEY_DURATION)
      val durationSec = durationUs / 1_000_000.0 // convert duration to seconds

      return (fileSize / durationSec).toInt()/1000 // bitrate in bits per second
    }
    fun getDestinationBitrateByQuality(path: String, quality: String): Int {
      val originalBitrate = getAudioBitrate(path)
      var destinationBitrate = originalBitrate
      Utils.addLog("source bitrate: $originalBitrate")

      when (quality.lowercase()) {
        "low" -> destinationBitrate = maxOf(64, (originalBitrate * 0.3).toInt())
        "medium" -> destinationBitrate = (originalBitrate * 0.5).toInt()
        "high" -> destinationBitrate = minOf(320, (originalBitrate * 0.7).toInt())
        else -> Utils.addLog("Invalid quality level. Please enter 'low', 'medium', or 'high'.")
      }

      return destinationBitrate
    }

  }
}
