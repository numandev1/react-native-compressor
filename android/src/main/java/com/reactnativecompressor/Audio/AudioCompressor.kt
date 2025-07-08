package com.reactnativecompressor.Audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import androidx.annotation.RequiresApi
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableMap
import com.reactnativecompressor.Utils.MediaCache
import com.reactnativecompressor.Utils.Utils
import com.reactnativecompressor.Utils.Utils.addLog

class AudioCompressor {
  companion object {
    private const val TIMEOUT_USEC = 10000L
    private const val AAC_MIME_TYPE = "audio/mp4a-latm"

    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
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

      try {
        var inputPath = filePathWithoutFileUri
        var isNonWav = false

        // Handle MP4 files by extracting audio first
        if (fileUrl.endsWith(".mp4", ignoreCase = true)) {
          addLog("mp4 file found")
          val mp3Path = Utils.generateCacheFilePath("mp3", context)
          AudioExtractor().genVideoUsingMuxer(fileUrl, mp3Path, -1, -1, true, false)
          inputPath = mp3Path
          isNonWav = true
        }

        compressAudioWithMediaCodec(inputPath, filePathWithoutFileUri, optionMap, context) { outputPath, success ->
          if (success) {
            val returnableFilePath = "file://$outputPath"
            addLog("finished: $returnableFilePath")
            MediaCache.removeCompletedImagePath(fileUrl)
            promise.resolve(returnableFilePath)
          } else {
            addLog("error: $outputPath")
            promise.resolve(_fileUrl)
          }
        }
      } catch (e: Exception) {
        addLog("Exception in CompressAudio: ${e.message}")
        promise.resolve(_fileUrl)
      }
    }

    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private fun compressAudioWithMediaCodec(
      inputPath: String,
      originalPath: String,
      optionMap: ReadableMap,
      context: ReactApplicationContext,
      callback: (String, Boolean) -> Unit
    ) {
      val options = AudioHelper.fromMap(optionMap)
      val outputPath = Utils.generateCacheFilePath("m4a", context)

      var extractor: MediaExtractor? = null
      var muxer: MediaMuxer? = null
      var encoder: MediaCodec? = null

      try {
        // Setup extractor
        extractor = MediaExtractor()
        extractor.setDataSource(inputPath)

        val audioTrackIndex = selectAudioTrack(extractor)
        if (audioTrackIndex < 0) {
          callback("No audio track found", false)
          return
        }

        extractor.selectTrack(audioTrackIndex)
        val inputFormat = extractor.getTrackFormat(audioTrackIndex)

        // Get input audio properties
        val inputSampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val inputChannelCount = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

        // Calculate output parameters
        val outputSampleRate = if (options.samplerate != -1) options.samplerate else inputSampleRate
        val outputChannelCount = if (options.channels != -1) options.channels else inputChannelCount
        val outputBitrate = if (options.bitrate != -1) {
          options.bitrate
        } else {
          AudioHelper.getDestinationBitrateByQuality(originalPath, options.quality!!) * 1000
        }

        // Setup encoder
        val outputFormat = MediaFormat.createAudioFormat(AAC_MIME_TYPE, outputSampleRate, outputChannelCount)
        outputFormat.setInteger(MediaFormat.KEY_BIT_RATE, outputBitrate)
        outputFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)

        encoder = MediaCodec.createEncoderByType(AAC_MIME_TYPE)
        encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()

        // Setup muxer
        muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        // Process audio
        val success = processAudio(extractor, encoder, muxer, inputFormat, outputFormat)

        callback(outputPath, success)

      } catch (e: Exception) {
        addLog("Error in compressAudioWithMediaCodec: ${e.message}")
        callback("Error: ${e.message}", false)
      } finally {
        try {
          encoder?.stop()
          encoder?.release()
          muxer?.stop()
          muxer?.release()
          extractor?.release()
        } catch (e: Exception) {
          addLog("Error releasing resources: ${e.message}")
        }
      }
    }

    private fun selectAudioTrack(extractor: MediaExtractor): Int {
      val trackCount = extractor.trackCount
      for (i in 0 until trackCount) {
        val format = extractor.getTrackFormat(i)
        val mime = format.getString(MediaFormat.KEY_MIME)
        if (mime?.startsWith("audio/") == true) {
          return i
        }
      }
      return -1
    }

    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private fun processAudio(
      extractor: MediaExtractor,
      encoder: MediaCodec,
      muxer: MediaMuxer,
      inputFormat: MediaFormat,
      outputFormat: MediaFormat
    ): Boolean {
      var muxerTrackIndex = -1
      var muxerStarted = false
      val bufferInfo = MediaCodec.BufferInfo()

      try {
        while (true) {
          // Feed input to encoder
          val inputBufferIndex = encoder.dequeueInputBuffer(TIMEOUT_USEC)
          if (inputBufferIndex >= 0) {
            val inputBuffer = encoder.getInputBuffer(inputBufferIndex)
            val sampleSize = extractor.readSampleData(inputBuffer!!, 0)

            if (sampleSize < 0) {
              // End of stream
              encoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            } else {
              val presentationTimeUs = extractor.sampleTime
              encoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, presentationTimeUs, 0)
              extractor.advance()
            }
          }

          // Get output from encoder
          val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC)
          when {
            outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
              if (muxerStarted) {
                throw RuntimeException("Format changed twice")
              }
              val newFormat = encoder.outputFormat
              muxerTrackIndex = muxer.addTrack(newFormat)
              muxer.start()
              muxerStarted = true
            }
            outputBufferIndex >= 0 -> {
              val outputBuffer = encoder.getOutputBuffer(outputBufferIndex)
              if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                bufferInfo.size = 0
              }

              if (bufferInfo.size != 0) {
                if (!muxerStarted) {
                  throw RuntimeException("Muxer hasn't started")
                }
                outputBuffer!!.position(bufferInfo.offset)
                outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                muxer.writeSampleData(muxerTrackIndex, outputBuffer, bufferInfo)
              }

              encoder.releaseOutputBuffer(outputBufferIndex, false)

              if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                break
              }
            }
          }
        }
        return true
      } catch (e: Exception) {
        addLog("Error in processAudio: ${e.message}")
        return false
      }
    }
  }
}
