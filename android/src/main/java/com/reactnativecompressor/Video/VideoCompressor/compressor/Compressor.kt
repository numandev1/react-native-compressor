package com.reactnativecompressor.Video.VideoCompressor.compressor

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Log
import com.reactnativecompressor.Video.VideoCompressor.CompressionProgressListener
import com.reactnativecompressor.Video.VideoCompressor.utils.CompressorUtils.findTrack
import com.reactnativecompressor.Video.VideoCompressor.utils.CompressorUtils.hasQTI
import com.reactnativecompressor.Video.VideoCompressor.utils.CompressorUtils.prepareVideoHeight
import com.reactnativecompressor.Video.VideoCompressor.utils.CompressorUtils.prepareVideoWidth
import com.reactnativecompressor.Video.VideoCompressor.utils.CompressorUtils.printException
import com.reactnativecompressor.Video.VideoCompressor.utils.CompressorUtils.setOutputFileParameters
import com.reactnativecompressor.Video.VideoCompressor.utils.CompressorUtils.setUpMP4Movie
import com.reactnativecompressor.Video.VideoCompressor.utils.StreamableVideo
import com.reactnativecompressor.Video.VideoCompressor.video.InputSurface
import com.reactnativecompressor.Video.VideoCompressor.video.MP4Builder
import com.reactnativecompressor.Video.VideoCompressor.video.OutputSurface
import com.reactnativecompressor.Video.VideoCompressor.video.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

object Compressor {

  // Minimum bitrate for compression (2Mbps)
  private const val MIN_BITRATE = 2000000

  // MIME type for H.264 Advanced Video Coding
  private const val MIME_TYPE = "video/avc"

  // Default timeout for MediaCodec operations
  private const val MEDIACODEC_TIMEOUT_DEFAULT = 100L

  // Error message for invalid bitrate
  private const val INVALID_BITRATE =
    "The provided bitrate is smaller than what is needed for compression, " +
      "try to set isMinBitRateEnabled to false"
  private val SUPPORTED_AUDIO_SAMPLE_RATES = setOf(
    8000, 11025, 12000, 16000, 22050, 24000, 32000, 44100, 48000, 64000
  )
  private const val STREAMABLE_SUFFIX = "-streamable"
  private const val DEFAULT_OUTPUT_EXTENSION = "mp4"

  // Flag to check if compression is running
  var isRunning = true

  private fun getStreamableOutputFile(cacheFile: File): File =
    File(
      cacheFile.parentFile ?: File("."),
      "${cacheFile.nameWithoutExtension}$STREAMABLE_SUFFIX.${cacheFile.extension.ifEmpty { DEFAULT_OUTPUT_EXTENSION }}"
    )

  suspend fun compressVideo(
    index: Int,
    context: Context,
    srcUri: Uri,
    destination: String,
    streamableFile: String?,
    outputWidth: Int,
    outputHeight: Int,
    outputBitrate: Int,
    outputFrameRate: Int,
    disableAudio: Boolean = false,
    listener: CompressionProgressListener,
  ): Result = withContext(Dispatchers.Default) {

    // Initialize MediaExtractor and MediaMetadataRetriever
    val extractor = MediaExtractor()
    val mediaMetadataRetriever = MediaMetadataRetriever()

    try {
      // Set the data source for the MediaMetadataRetriever
      mediaMetadataRetriever.setDataSource(context, srcUri)
    } catch (exception: IllegalArgumentException) {
      printException(exception)
      return@withContext Result(
        index,
        success = false,
        failureMessage = "${exception.message}"
      )
    }

    runCatching {
      // Set the data source for the MediaExtractor
      extractor.setDataSource(context, srcUri, null)
    }

    // Retrieve video metadata
    val height: Double = prepareVideoHeight(mediaMetadataRetriever)
    val width: Double = prepareVideoWidth(mediaMetadataRetriever)
    val rotationData = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
    val bitrateData = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
    val durationData = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
    val locationData = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)

    // Check if any metadata is missing
    if (rotationData.isNullOrEmpty() || bitrateData.isNullOrEmpty() || durationData.isNullOrEmpty()) {
      // Exit execution
      return@withContext Result(
        index,
        success = false,
        failureMessage = "Failed to extract video metadata, please try again"
      )
    }

    var rotation = rotationData.toInt()
    val duration = durationData.toLong() * 1000

    // Handle new bitrate value
    val newBitrate: Int = outputBitrate

    // Handle new width and height values
    var (newWidth, newHeight) = Pair(outputWidth, outputHeight)

    // Handle rotation values and swapping height and width if needed
    rotation = when (rotation) {
      90, 270 -> {
        val tempHeight = newHeight
        newHeight = newWidth
        newWidth = tempHeight
        0
      }
      180 -> 0
      else -> rotation
    }

    // Start video compression
    return@withContext start(
      index,
      newWidth!!,
      newHeight!!,
      destination,
      newBitrate,
      outputFrameRate,
      streamableFile,
      disableAudio,
      extractor,
      listener,
      duration,
      rotation,
      locationData
    )
  }

  // Function to start video compression
  @Suppress("DEPRECATION")
  private fun start(
    id: Int,
    newWidth: Int,
    newHeight: Int,
    destination: String,
    newBitrate: Int,
    outputFrameRate: Int,
    streamableFile: String?,
    disableAudio: Boolean,
    extractor: MediaExtractor,
    compressionProgressListener: CompressionProgressListener,
    duration: Long,
    rotation: Int,
    location: String? = null
  ): Result {
    // Check if newWidth and newHeight are valid
    if (newWidth != 0 && newHeight != 0) {
      // Create a cache file for the compressed video
      val cacheFile = File(destination)

      try {
        // MediaCodec accesses encoder and decoder components and processes the new video
        // input to generate a compressed/smaller size video
        val bufferInfo = MediaCodec.BufferInfo()

        // Setup mp4 movie
        val movie = setUpMP4Movie(rotation, cacheFile, location)

        // MediaMuxer outputs MP4 in this app
        val mediaMuxer = MP4Builder().createMovie(movie)

        // Start with the video track
        val videoIndex = findTrack(extractor, isVideo = true)

        extractor.selectTrack(videoIndex)
        extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        val inputFormat = extractor.getTrackFormat(videoIndex)

        val outputFormat: MediaFormat =
          MediaFormat.createVideoFormat(MIME_TYPE, newWidth, newHeight)

        // Set output format
        setOutputFileParameters(
          inputFormat,
          outputFormat,
          newBitrate,
          outputFrameRate,
        )

        val decoder: MediaCodec

        // Check if QTI hardware acceleration is available
        val hasQTI = hasQTI()

        // Prepare the video encoder
        val encoder = prepareEncoder(outputFormat, hasQTI)

        val inputSurface: InputSurface
        val outputSurface: OutputSurface

        try {
          var inputDone = false
          var outputDone = false

          var videoTrackIndex = -5

          // Frame dropping: when source fps is higher than target output fps,
          // skip decoded frames whose PTS falls before the next target slot.
          // Saves GL render + encoder work proportional to the drop ratio
          // (e.g. 60fps → 30fps cuts pipeline work roughly in half).
          val targetFrameIntervalUs: Long =
            if (outputFrameRate > 0) 1_000_000L / outputFrameRate else 0L
          var nextTargetPtsUs: Long = 0L

          inputSurface = InputSurface(encoder.createInputSurface())
          inputSurface.makeCurrent()
          // Move to executing state
          encoder.start()

          outputSurface = OutputSurface()

          decoder = prepareDecoder(inputFormat, outputSurface)

          // Move to executing state
          decoder.start()

          while (!outputDone) {
            if (!inputDone) {
                            // Feed the decoder until it has no free input slots or the
                            // extractor is empty. HW codecs typically have 4-8 input
                            // slots; queuing only one sample per outer iteration starves
                            // the pipeline and forces serial decode-render-encode.
                            feedLoop@ while (!inputDone) {
                                val index = extractor.sampleTrackIndex

                                if (index == videoIndex) {
                                    val inputBufferIndex =
                                        decoder.dequeueInputBuffer(0L)
                                    if (inputBufferIndex < 0) break@feedLoop
                                    val inputBuffer = decoder.getInputBuffer(inputBufferIndex)
                                    val chunkSize = extractor.readSampleData(inputBuffer!!, 0)
                                    when {
                                        chunkSize < 0 -> {
                                            decoder.queueInputBuffer(
                                                inputBufferIndex,
                                                0,
                                                0,
                                                0L,
                                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                            )
                                            inputDone = true
                                        }
                                        else -> {
                                            decoder.queueInputBuffer(
                                                inputBufferIndex,
                                                0,
                                                chunkSize,
                                                extractor.sampleTime,
                                                0
                                            )
                                            extractor.advance()
                                        }
                                    }
                                } else if (index == -1) { //end of file
                                    val inputBufferIndex =
                                        decoder.dequeueInputBuffer(0L)
                                    if (inputBufferIndex < 0) break@feedLoop
                                    decoder.queueInputBuffer(
                                        inputBufferIndex,
                                        0,
                                        0,
                                        0L,
                                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                    )
                                    inputDone = true
                                } else {
                                    // Different track type at head of extractor (audio etc.).
                                    break@feedLoop
                                }
                            }
                        }

                        var decoderOutputAvailable = true
                        var encoderOutputAvailable = true

                        loop@ while (decoderOutputAvailable || encoderOutputAvailable) {

                            if (!isRunning) {
                                dispose(
                                    videoIndex,
                                    decoder,
                                    encoder,
                                    inputSurface,
                                    outputSurface,
                                    extractor
                                )

                                compressionProgressListener.onProgressCancelled(id)
                                return Result(
                                    id,
                                    success = false,
                                    failureMessage = "The compression has stopped!"
                                )
                            }

                            //Encoder
                            val encoderStatus =
                                encoder.dequeueOutputBuffer(bufferInfo, MEDIACODEC_TIMEOUT_DEFAULT)

                            when {
                                encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER -> encoderOutputAvailable =
                                    false
                                encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                                    val newFormat = encoder.outputFormat
                                    if (videoTrackIndex == -5)
                                        videoTrackIndex = mediaMuxer.addTrack(newFormat, false)
                                }
                                encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> {
                                    // ignore this status
                                }
                                encoderStatus < 0 -> throw RuntimeException("unexpected result from encoder.dequeueOutputBuffer: $encoderStatus")
                                else -> {
                                    val encodedData = encoder.getOutputBuffer(encoderStatus)
                                        ?: throw RuntimeException("encoderOutputBuffer $encoderStatus was null")

                                    if (bufferInfo.size > 1) {
                                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                                            mediaMuxer.writeSampleData(
                                                videoTrackIndex,
                                                encodedData, bufferInfo, false
                                            )
                                        }

                                    }

                                    outputDone =
                                        bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                                    encoder.releaseOutputBuffer(encoderStatus, false)
                                }
                            }
                            if (encoderStatus != MediaCodec.INFO_TRY_AGAIN_LATER) continue@loop

                            //Decoder
                            val decoderStatus =
                                decoder.dequeueOutputBuffer(bufferInfo, MEDIACODEC_TIMEOUT_DEFAULT)
                            when {
                                decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER -> decoderOutputAvailable =
                                    false
                                decoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> {
                                    // ignore this status
                                }
                                decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                                    // ignore this status
                                }
                                decoderStatus < 0 -> throw RuntimeException("unexpected result from decoder.dequeueOutputBuffer: $decoderStatus")
                                else -> {
                                    val isEos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                                    var doRender = bufferInfo.size != 0 && !isEos

                                    // Drop frames whose PTS falls before the next target slot.
                                    // Only kicks in when targetFrameIntervalUs > 0 and the source
                                    // is producing faster than the target frame rate.
                                    if (doRender && targetFrameIntervalUs > 0L) {
                                        if (bufferInfo.presentationTimeUs < nextTargetPtsUs) {
                                            doRender = false
                                        } else {
                                            nextTargetPtsUs = bufferInfo.presentationTimeUs + targetFrameIntervalUs
                                        }
                                    }

                                    decoder.releaseOutputBuffer(decoderStatus, doRender)
                                    if (doRender) {
                                        var errorWait = false
                                        try {
                                            outputSurface.awaitNewImage()
                                        } catch (e: Exception) {
                                            errorWait = true
                                            Log.e(
                                                "Compressor",
                                                e.message ?: "Compression failed at swapping buffer"
                                            )
                                        }

                                        if (!errorWait) {
                                            outputSurface.drawImage()

                                            inputSurface.setPresentationTime(bufferInfo.presentationTimeUs * 1000)

                                            compressionProgressListener.onProgressChanged(
                                                id,
                                                bufferInfo.presentationTimeUs.toFloat() / duration.toFloat() * 100
                                            )

                                            inputSurface.swapBuffers()
                                        }
                                    }
                                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                        decoderOutputAvailable = false
                                        encoder.signalEndOfInputStream()
                                    }
                                }
                            }
                        }
          }

        } catch (exception: Throwable) {
          printException(exception)
          return Result(id, success = false, failureMessage = exception.message)
        }

        // Release resources
        dispose(
          videoIndex,
          decoder,
          encoder,
          inputSurface,
          outputSurface,
          extractor
        )

        // Process audio if necessary
        processAudio(
          mediaMuxer = mediaMuxer,
          bufferInfo = bufferInfo,
          disableAudio = disableAudio,
          extractor
        )

        extractor.release()
        try {
          mediaMuxer.finishMovie()
        } catch (e: Throwable) {
          printException(e)
          return Result(id, success = false, failureMessage = e.message ?: "Failed to finalize compressed video")
        }

      } catch (exception: Throwable) {
        printException(exception)
        return Result(id, success = false, failureMessage = exception.message)
      }

      var resultFile = cacheFile

      // StreamableVideo rewrites the whole MP4 to move the moov atom to the front,
      // which doubles disk I/O. Only run it when the caller explicitly requested a
      // streamable copy (non-null streamableFile). Chat uploads do not need it.
      if (streamableFile != null) {
        try {
          val targetFile = File(streamableFile)
          val outputFile = if (targetFile.absolutePath == cacheFile.absolutePath) {
            getStreamableOutputFile(cacheFile)
          } else {
            targetFile
          }
          val result = StreamableVideo.start(`in` = cacheFile, out = outputFile)
          if (result) {
            if (targetFile.absolutePath == cacheFile.absolutePath) {
              cacheFile.delete()
              outputFile.renameTo(cacheFile)
              resultFile = cacheFile
            } else {
              resultFile = outputFile
              cacheFile.delete()
            }
          }
        } catch (e: Exception) {
          printException(e)
        }
      }
      if (!resultFile.exists() || resultFile.length() <= 32) {
        return Result(
          id,
          success = false,
          failureMessage = "Compressed video output is invalid"
        )
      }
      return Result(
        id,
        success = true,
        failureMessage = null,
        size = resultFile.length(),
        resultFile.path
      )
    }

    return Result(
      id,
      success = false,
      failureMessage = "Something went wrong, please try again"
    )
  }

  // Function to process audio
    private fun processAudio(
        mediaMuxer: MP4Builder,
        bufferInfo: MediaCodec.BufferInfo,
        disableAudio: Boolean,
        extractor: MediaExtractor
    ) {
        val audioIndex = findTrack(extractor, isVideo = false)
        if (audioIndex >= 0 && !disableAudio) {
            extractor.selectTrack(audioIndex)
            val audioFormat = extractor.getTrackFormat(audioIndex)
            if (!isSupportedAudioFormat(audioFormat)) {
                extractor.unselectTrack(audioIndex)
                return
            }
            val muxerTrackIndex = mediaMuxer.addTrack(audioFormat, true)
            var maxBufferSize = if (audioFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                audioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
            } else {
                64 * 1024
            }

            if (maxBufferSize <= 0) {
                maxBufferSize = 64 * 1024
            }

            var buffer: ByteBuffer = ByteBuffer.allocateDirect(maxBufferSize)
            if (Build.VERSION.SDK_INT >= 28) {
                val size = extractor.sampleSize
                if (size > maxBufferSize) {
                    maxBufferSize = (size + 1024).toInt()
                    buffer = ByteBuffer.allocateDirect(maxBufferSize)
                }
            }
            var inputDone = false
            extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            while (!inputDone) {
                val index = extractor.sampleTrackIndex
                if (index == audioIndex) {
                    bufferInfo.size = extractor.readSampleData(buffer, 0)

                    if (bufferInfo.size >= 0) {
                        bufferInfo.apply {
                            presentationTimeUs = extractor.sampleTime
                            offset = 0
                            flags = MediaCodec.BUFFER_FLAG_KEY_FRAME
                        }
                        mediaMuxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo, true)
                        extractor.advance()

                    } else {
                        bufferInfo.size = 0
                        inputDone = true
                    }
                } else if (index == -1) {
                    inputDone = true
                }
            }
            extractor.unselectTrack(audioIndex)
        }
    }

    private fun isSupportedAudioFormat(audioFormat: MediaFormat): Boolean {
        if (!audioFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE) ||
            !audioFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
            return false
        }
        val sampleRate = audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channelCount = audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        return channelCount > 0 && sampleRate in SUPPORTED_AUDIO_SAMPLE_RATES
    }

  // Function to prepare the video encoder
    private fun prepareEncoder(outputFormat: MediaFormat, hasQTI: Boolean): MediaCodec {
        // Prefer hardware AVC encoder while skipping known-broken QTI codec that
        // produces files unplayable on Mac/iOS (c2.qti.avc.encoder).
        val encoder = pickAvcEncoder(outputFormat, hasQTI)
        encoder.configure(
            outputFormat, null, null,
            MediaCodec.CONFIGURE_FLAG_ENCODE
        )

        Log.i("Compressor", "encoder selected: ${encoder.name}")

        return encoder
    }

    private fun pickAvcEncoder(outputFormat: MediaFormat, hasQTI: Boolean): MediaCodec {
        // ALL_CODECS surfaces vendor codecs that REGULAR_CODECS hides (e.g. some
        // Exynos / MTK HW encoders). We still filter blacklisted / SW codecs below.
        val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
        val candidates = codecList.codecInfos.filter { info ->
            info.isEncoder && info.supportedTypes.any { it.equals(MIME_TYPE, ignoreCase = true) }
        }

        fun isBlacklisted(name: String): Boolean {
            val lower = name.lowercase()
            return lower.contains("c2.qti.avc.encoder") || lower.contains("omx.qcom.video.encoder.avc.secure")
        }

        fun isSoftware(info: MediaCodecInfo): Boolean {
            val name = info.name.lowercase()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (info.isSoftwareOnly) return true
            }
            return name.startsWith("omx.google.") ||
                name.startsWith("c2.android.") ||
                name.contains(".sw.")
        }

        val supportsFormat = candidates.filter { info ->
            runCatching {
                info.getCapabilitiesForType(MIME_TYPE).isFormatSupported(outputFormat)
            }.getOrDefault(false) && !isBlacklisted(info.name)
        }

        val hardwareFirst = supportsFormat.firstOrNull { !isSoftware(it) }
        val chosen = hardwareFirst ?: supportsFormat.firstOrNull()

        if (chosen != null) {
            return MediaCodec.createByCodecName(chosen.name)
        }

        // Fallback: keep historical QTI-safe path when format probing fails.
        return if (hasQTI) {
            MediaCodec.createByCodecName("c2.android.avc.encoder")
        } else {
            MediaCodec.createEncoderByType(MIME_TYPE)
        }
    }

  // Function to prepare the video decoder
    private fun prepareDecoder(
        inputFormat: MediaFormat,
        outputSurface: OutputSurface,
    ): MediaCodec {
        val originalMime = inputFormat.getString(MediaFormat.KEY_MIME)!!

        // Dolby Vision (video/dolby-vision) has no standalone decoder on most Android
        // devices and throws NAME_NOT_FOUND. Profiles 8.1/8.4 carry an HEVC base layer
        // that the standard HEVC decoder can render. Profile 5 has no compatible base
        // layer and must be rejected upstream.
        val resolvedMime = if (originalMime.equals("video/dolby-vision", ignoreCase = true)) {
            val profile = if (inputFormat.containsKey(MediaFormat.KEY_PROFILE)) {
                inputFormat.getInteger(MediaFormat.KEY_PROFILE)
            } else {
                -1
            }
            // DV profile 5 = 0x20, no HEVC fallback. Profiles 8.x carry HEVC base layer.
            if (profile == 0x20) {
                throw IllegalStateException("Dolby Vision profile 5 has no HEVC base layer; cannot transcode")
            }
            inputFormat.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_VIDEO_HEVC)
            MediaFormat.MIMETYPE_VIDEO_HEVC
        } else {
            originalMime
        }

        val decoder = MediaCodec.createDecoderByType(resolvedMime)

        decoder.configure(inputFormat, outputSurface.getSurface(), null, 0)

        Log.i("Compressor", "decoder selected: ${decoder.name} mime=$resolvedMime")

        return decoder
    }

  // Function to release resources
    private fun dispose(
        videoIndex: Int,
        decoder: MediaCodec,
        encoder: MediaCodec,
        inputSurface: InputSurface,
        outputSurface: OutputSurface,
        extractor: MediaExtractor
    ) {
        extractor.unselectTrack(videoIndex)

        decoder.stop()
        decoder.release()

        encoder.stop()
        encoder.release()

        inputSurface.release()
        outputSurface.release()
    }
}
