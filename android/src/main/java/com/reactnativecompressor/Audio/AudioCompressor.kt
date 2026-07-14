package com.reactnativecompressor.Audio


import android.annotation.SuppressLint
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableMap
import com.naman14.androidlame.LameBuilder
import com.naman14.androidlame.WaveReader
import com.reactnativecompressor.Utils.MediaCache
import com.reactnativecompressor.Utils.Utils
import com.reactnativecompressor.Utils.Utils.addLog
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException

class AudioCompressor {
  companion object {
    val TAG = "AudioMain"
    private const val OUTPUT_STREAM_BUFFER = 8192

    var outputStream: BufferedOutputStream? = null
    var waveReader: WaveReader? = null

    @JvmStatic
    fun CompressAudio(
      fileUrl: String,
      optionMap: ReadableMap,
      context: ReactApplicationContext,
      promise: Promise,
    ) {
      val realPath = Utils.getRealPath(fileUrl, context)
      var _fileUrl = realPath
      val filePathWithoutFileUri = realPath!!.replace("file://", "")

      // These are declared outside the try block so they can be cleaned up
      // in the catch handler. They must be nullable because the transcoding
      // branch may not execute.
      var wavPath: String? = null
      var isNonWav: Boolean = false

      try {
        wavPath = filePathWithoutFileUri
        // LAME's WaveReader requires PCM 16-bit LE WAV. Anything else
        // (m4a/AAC, mp3, ogg, flac, video audio tracks, ...) must be
        // decoded to WAV first via the platform MediaCodec pipeline —
        // jlayer's MP3-only Converter silently produced empty WAVs for
        // every non-MP3 input, which LAME then encoded as a few seconds
        // of silence (see #410 thread, m4a/AAC support).
        //
        // Check the resolved local path, not the original fileUrl: a
        // content:// or remote URL won't carry the file extension, so
        // checking fileUrl would mis-classify WAV inputs as non-WAV and
        // route them through an unnecessary (and potentially failing)
        // MediaCodec decode pass.
        if (!filePathWithoutFileUri.endsWith(".wav", ignoreCase = true)) {
          addLog("non wav file found, transcoding to PCM WAV")
          wavPath = Utils.generateCacheFilePath("wav", context)
          try {
            AudioTranscoder.decodeToWav(filePathWithoutFileUri, wavPath)
          } catch (e: IOException) {
            addLog("AudioTranscoder failed: " + e.localizedMessage)
            // Surface the failure rather than silently returning a
            // degenerate MP3 of silence.
            File(wavPath).delete()
            promise.reject("audio_transcode_failed", e.localizedMessage ?: "Audio decode failed", e)
            return
          }
          isNonWav = true
        }

        autoCompressHelper(wavPath, filePathWithoutFileUri, optionMap, context) { mp3Path, finished ->
          if (finished) {
            val returnableFilePath: String = "file://$mp3Path"
            addLog("finished: " + returnableFilePath)
            MediaCache.removeCompletedImagePath(fileUrl)
            if (isNonWav) {
              File(wavPath).delete()
            }
            promise.resolve(returnableFilePath)
          } else {
            addLog("error: " + mp3Path)
            // Clean up the temp WAV on failure too; previously it was
            // leaked because only the success path deleted it.
            if (isNonWav) {
              File(wavPath).delete()
            }
            promise.resolve(_fileUrl)
          }
        }
      } catch (e: Exception) {
        addLog("CompressAudio failed: " + e.localizedMessage)
        // Clean up any temp WAV that was created before the failure.
        val temp = wavPath
        if (isNonWav && temp != null) {
          try {
            File(temp).delete()
          } catch (_: Throwable) {
            // ignore cleanup failures
          }
        }
        promise.reject("audio_compress_failed", e.localizedMessage ?: "Audio compression failed", e)
      }
    }

    @SuppressLint("WrongConstant")
    private fun autoCompressHelper(
      fileUrl: String,
      actualFileUrl: String,
      optionMap: ReadableMap,
      context: ReactApplicationContext,
      completeCallback: (String, Boolean) -> Unit
    ) {

      val options = AudioHelper.fromMap(optionMap)
      val quality = options.quality

      var isCompletedCallbackTriggered: Boolean = false
      try {
        var mp3Path = Utils.generateCacheFilePath("mp3", context)
        val input = File(fileUrl)
        val output = File(mp3Path)

        val CHUNK_SIZE = 8192
        addLog("Initialising wav reader")

        waveReader = WaveReader(input)

        try {
          waveReader!!.openWave()
        } catch (e: IOException) {
          addLog("openWave failed: " + e.localizedMessage)
          completeCallback(e.localizedMessage ?: "Failed to open WAV", false)
          return
        }

        addLog("Intitialising encoder")


        // for bitrate
        var audioBitrate: Int
        if (options.bitrate != -1) {
          audioBitrate = options.bitrate / 1000
        } else {
          audioBitrate = AudioHelper.getDestinationBitrateByQuality(actualFileUrl, quality!!)
          Utils.addLog("dest bitrate: $audioBitrate")
        }

        var androidLame = LameBuilder();
        androidLame.setOutBitrate(audioBitrate)

        // for channels
        var audioChannels: Int
        if (options.channels != -1) {
          audioChannels = options.channels!!
        } else {
          audioChannels = waveReader!!.channels
        }
        androidLame.setOutChannels(audioChannels)

        // for sample rate
        androidLame.setInSampleRate(waveReader!!.sampleRate)
        var audioSampleRate: Int
        if (options.samplerate != -1) {
          audioSampleRate = options.samplerate!!
        } else {
          audioSampleRate = waveReader!!.sampleRate
        }
        androidLame.setOutSampleRate(audioSampleRate)
        val androidLameBuild = androidLame.build()

        try {
          outputStream = BufferedOutputStream(FileOutputStream(output), OUTPUT_STREAM_BUFFER)
        } catch (e: FileNotFoundException) {
          addLog("Failed to create output stream: " + e.localizedMessage)
          completeCallback(e.localizedMessage ?: "Failed to create output file", false)
          return
        }

        var bytesRead = 0

        val buffer_l = ShortArray(CHUNK_SIZE)
        val buffer_r = ShortArray(CHUNK_SIZE)
        val mp3Buf = ByteArray(CHUNK_SIZE)

        val channels = waveReader!!.channels

        addLog("started encoding")
        while (true) {
          try {
            if (channels == 2) {

              bytesRead = waveReader!!.read(buffer_l, buffer_r, CHUNK_SIZE)
              addLog("bytes read=$bytesRead")

              if (bytesRead > 0) {

                var bytesEncoded = 0
                bytesEncoded = androidLameBuild.encode(buffer_l, buffer_r, bytesRead, mp3Buf)
                addLog("bytes encoded=$bytesEncoded")

                if (bytesEncoded > 0) {
                  try {
                    addLog("writing mp3 buffer to outputstream with $bytesEncoded bytes")
                    outputStream!!.write(mp3Buf, 0, bytesEncoded)
                  } catch (e: IOException) {
                    e.printStackTrace()
                  }

                }

              } else
                break
            } else {

              bytesRead = waveReader!!.read(buffer_l, CHUNK_SIZE)
              addLog("bytes read=$bytesRead")

              if (bytesRead > 0) {
                var bytesEncoded = 0

                bytesEncoded = androidLameBuild.encode(buffer_l, buffer_l, bytesRead, mp3Buf)
                addLog("bytes encoded=$bytesEncoded")

                if (bytesEncoded > 0) {
                  try {
                    addLog("writing mp3 buffer to outputstream with $bytesEncoded bytes")
                    outputStream!!.write(mp3Buf, 0, bytesEncoded)
                  } catch (e: IOException) {
                    e.printStackTrace()
                  }

                }

              } else
                break
            }


          } catch (e: IOException) {
            e.printStackTrace()
          }

        }

        addLog("flushing final mp3buffer")
        val outputMp3buf = androidLameBuild.flush(mp3Buf)
        addLog("flushed $outputMp3buf bytes")
        if (outputMp3buf > 0) {
          try {
            addLog("writing final mp3buffer to outputstream")
            outputStream!!.write(mp3Buf, 0, outputMp3buf)
            addLog("closing output stream")
            outputStream!!.close()
            completeCallback(output.absolutePath, true)
            isCompletedCallbackTriggered = true
          } catch (e: IOException) {
            completeCallback(e.localizedMessage, false)
            isCompletedCallbackTriggered = true
            e.printStackTrace()
          }
        }

      } catch (e: IOException) {
        completeCallback(e.localizedMessage, false)
        isCompletedCallbackTriggered = true
      }
      if (!isCompletedCallbackTriggered) {
        completeCallback("something went wrong", false)
      }
    }


  }
}
