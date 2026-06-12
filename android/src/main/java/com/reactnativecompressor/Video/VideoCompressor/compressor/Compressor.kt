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
import com.reactnativecompressor.Video.VideoCompressor.utils.CompressorUtils.ensureDecodableVideoFormat
import com.reactnativecompressor.Video.VideoCompressor.utils.CompressorUtils.findTrack
import com.reactnativecompressor.Video.VideoCompressor.utils.CompressorUtils.hasQTI
import com.reactnativecompressor.Video.VideoCompressor.utils.CompressorUtils.prepareVideoHeight
import com.reactnativecompressor.Video.VideoCompressor.utils.CompressorUtils.prepareVideoWidth
import com.reactnativecompressor.Video.VideoCompressor.utils.CompressorUtils.printException
import com.reactnativecompressor.Video.VideoCompressor.utils.CompressorUtils.setOutputFileParameters
import com.reactnativecompressor.Video.VideoCompressor.utils.CompressorUtils.setUpMP4Movie
import com.reactnativecompressor.Video.VideoCompressor.utils.LocationExtractor
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
    // ISO 6709 string (e.g. "+37.4220-122.0840/"). Forwarded into the
    // output udta/©xyz box so GPS metadata survives the rewrite.
    //
    // Some Samsung firmwares (S10 / Android 12) place "©xyz" in the
    // per-track udta, or use a 'loci' box, or iTunes-style meta/keys+ilst.
    // MediaMetadataRetriever only reads moov/udta/©xyz — returning null
    // (or empty) and dropping GPS. Fall back to a raw MP4 walker that
    // scans the whole file for every known location encoding.
    val retrievedLocation =
      mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)
    val locationData = if (!retrievedLocation.isNullOrEmpty()) {
      retrievedLocation
    } else {
      LocationExtractor.extract(context, srcUri)
    }
    // Never log the resolved ISO 6709 string — it is the user's exact GPS
    // coordinates. Log only presence and which mechanism resolved it.
    val locationSource = when {
      !retrievedLocation.isNullOrEmpty() -> "retriever"
      !locationData.isNullOrEmpty() -> "extractor"
      else -> "none"
    }
    Log.i("Compressor", "source location resolved: hasLocation=${!locationData.isNullOrEmpty()} source=$locationSource")

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
      locationData,
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
    location: String?,
  ): Result {
    // Check if newWidth and newHeight are valid
    if (newWidth != 0 && newHeight != 0) {
      // Create a cache file for the compressed video
      val cacheFile = File(destination)

      // Hoisted so the outer catch can close the muxer even though the val
      // below is scoped to the try. Stays null until createMovie() succeeds.
      var muxer: MP4Builder? = null

      try {
        // MediaCodec accesses encoder and decoder components and processes the new video
        // input to generate a compressed/smaller size video
        val bufferInfo = MediaCodec.BufferInfo()

        // Resolve the source video track and its format BEFORE allocating the
        // muxer, encoder or EGL surfaces. Dolby Vision profile 5 has no HEVC
        // base layer and cannot be transcoded; rejecting it here — instead of
        // inside prepareDecoder, after the muxer file stream, encoder and EGL
        // surfaces are already live — avoids leaking those resources on bail-out.
        val videoIndex = findTrack(extractor, isVideo = true)

        extractor.selectTrack(videoIndex)
        extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        val inputFormat = extractor.getTrackFormat(videoIndex)

        if (isUnsupportedDolbyVision(inputFormat)) {
          runCatching { extractor.release() }
          return Result(
            id,
            success = false,
            failureMessage = "Dolby Vision profile 5 has no HEVC base layer; cannot transcode"
          )
        }

        // Setup mp4 movie
        val movie = setUpMP4Movie(rotation, cacheFile, location)

        // MediaMuxer outputs MP4 in this app
        val mediaMuxer = MP4Builder().createMovie(movie)
        muxer = mediaMuxer

        val outputFormat: MediaFormat =
          MediaFormat.createVideoFormat(MIME_TYPE, newWidth, newHeight)

        // Set output format
        setOutputFileParameters(
          inputFormat,
          outputFormat,
          newBitrate,
          outputFrameRate,
        )

        // Check if QTI hardware acceleration is available
        val hasQTI = hasQTI()

        // Prepare the video encoder. If the encoder rejects the throughput-tuned
        // format at configure() time, prepareEncoder reconfigures using this
        // baseline format (same params, no VBR/priority/operating-rate keys).
        val encoder = prepareEncoder(outputFormat, hasQTI) {
          MediaFormat.createVideoFormat(MIME_TYPE, newWidth, newHeight).also {
            setOutputFileParameters(
              inputFormat,
              it,
              newBitrate,
              outputFrameRate,
              applyThroughputTuning = false,
            )
          }
        }

        // Track pipeline handles as they come up so a failure mid-setup
        // (EGL/GL init, decoder configure, encoder.start) releases whatever was
        // already created instead of leaking it. The encoder above is always
        // non-null by this point.
        var decoderRef: MediaCodec? = null
        var inputSurfaceRef: InputSurface? = null
        var outputSurfaceRef: OutputSurface? = null

        try {
          var inputDone = false
          var outputDone = false

          var videoTrackIndex = -5

          // Frame dropping: when source fps is higher than target output fps,
          // skip decoded frames whose PTS falls before the next target slot.
          // Saves GL render + encoder work proportional to the drop ratio
          // (e.g. 60fps → 30fps cuts pipeline work roughly in half).
          //
          // Only enable dropping when the source frame rate is reliably
          // higher than the target. If the source advertises 30 fps and the
          // target is 30 fps, even tiny PTS jitter can push a frame just
          // before its slot, get it dropped, and turn 30 fps output into
          // 20 fps — the choppy playback users reported.
          val sourceFrameRate: Int = if (inputFormat.containsKey(MediaFormat.KEY_FRAME_RATE)) {
            inputFormat.getInteger(MediaFormat.KEY_FRAME_RATE)
          } else 0
          val shouldDropFrames = outputFrameRate > 0 &&
            sourceFrameRate > 0 &&
            outputFrameRate < sourceFrameRate
          val targetFrameIntervalUs: Long =
            if (shouldDropFrames) 1_000_000L / outputFrameRate else 0L
          var nextTargetPtsUs: Long = 0L

          val inputSurface = InputSurface(encoder.createInputSurface())
          inputSurfaceRef = inputSurface
          inputSurface.makeCurrent()
          // Move to executing state
          encoder.start()

          val outputSurface = OutputSurface()
          outputSurfaceRef = outputSurface

          val decoder = prepareDecoder(inputFormat, outputSurface)
          decoderRef = decoder

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
                                    // Anchor the next slot to the ideal grid (previous slot +
                                    // interval) instead of to the actual PTS — anchoring to PTS
                                    // lets source-side jitter compound into extra drops, which
                                    // collapses the output frame rate well below the target.
                                    if (doRender && targetFrameIntervalUs > 0L) {
                                        if (bufferInfo.presentationTimeUs < nextTargetPtsUs) {
                                            doRender = false
                                        } else {
                                            nextTargetPtsUs += targetFrameIntervalUs
                                            // Snap forward when the source skips past a slot
                                            // (gap, seek, very low source fps) so the gate doesn't
                                            // burst-emit every following frame.
                                            if (bufferInfo.presentationTimeUs >= nextTargetPtsUs) {
                                                nextTargetPtsUs = bufferInfo.presentationTimeUs + targetFrameIntervalUs
                                            }
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
          // Release whatever was initialized before the failure. Setup errors
          // (EGL/GL init, decoder configure, encoder.start) and in-loop throws
          // land here; without this the encoder + EGL surfaces would leak and
          // break the next compression. dispose() tolerates the null handles
          // that occur when the failure happens mid-setup.
          dispose(
            videoIndex,
            decoderRef,
            encoder,
            inputSurfaceRef,
            outputSurfaceRef,
            extractor
          )
          // finishMovie() never runs on this path, so close the MP4Builder
          // streams explicitly or the output file handle leaks.
          mediaMuxer.close()
          return Result(id, success = false, failureMessage = exception.message)
        }

        // Release resources
        dispose(
          videoIndex,
          decoderRef,
          encoder,
          inputSurfaceRef,
          outputSurfaceRef,
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
          // finishMovie() may throw before it closes its own streams; close
          // them here so a finalize failure doesn't leak the file handle.
          mediaMuxer.close()
          return Result(id, success = false, failureMessage = e.message ?: "Failed to finalize compressed video")
        }

      } catch (exception: Throwable) {
        printException(exception)
        // Covers throws after the inner pipeline closed (e.g. processAudio,
        // extractor.release) where the MP4Builder is still open. close() is
        // idempotent, so calling it after a successful finishMovie() is a no-op.
        muxer?.close()
        return Result(id, success = false, failureMessage = exception.message)
      }

      var resultFile = cacheFile

      try {
        // Keep default outputs browser/progressive-playback compatible by moving the
        // MP4 moov atom in front of the media data. This runs for every output; when
        // no explicit streamableFile is requested, the rewritten copy replaces cacheFile.
        val targetFile = streamableFile?.let { File(it) } ?: getStreamableOutputFile(cacheFile)
        val outputFile = if (targetFile.absolutePath == cacheFile.absolutePath) {
          getStreamableOutputFile(cacheFile)
        } else {
          targetFile
        }
        val result = StreamableVideo.start(`in` = cacheFile, out = outputFile)
        if (result) {
          if (streamableFile == null || targetFile.absolutePath == cacheFile.absolutePath) {
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
    private fun prepareEncoder(
        outputFormat: MediaFormat,
        hasQTI: Boolean,
        baselineFormatProvider: () -> MediaFormat,
    ): MediaCodec {
        // Prefer hardware AVC encoder while skipping known-broken QTI codec that
        // produces files unplayable on Mac/iOS (c2.qti.avc.encoder).
        val encoder = pickAvcEncoder(outputFormat, hasQTI)
        try {
            encoder.configure(
                outputFormat, null, null,
                MediaCodec.CONFIGURE_FLAG_ENCODE
            )
            Log.i("Compressor", "encoder selected: ${encoder.name}")
            return encoder
        } catch (e: Exception) {
            // Some encoders reject the throughput-tuning keys (VBR bitrate mode,
            // priority, operating rate) at configure() time. A codec that throws
            // from configure() is unusable, so release it and retry on a fresh
            // codec with a baseline format (default rate control) rather than
            // failing the whole compression.
            Log.w(
                "Compressor",
                "encoder.configure rejected tuned format; retrying with default settings",
                e
            )
            runCatching { encoder.release() }
        }

        val baseline = baselineFormatProvider()
        val fallback = pickAvcEncoder(baseline, hasQTI)
        try {
            fallback.configure(
                baseline, null, null,
                MediaCodec.CONFIGURE_FLAG_ENCODE
            )
        } catch (e: Exception) {
            // Even the baseline format was rejected; release the codec so it
            // doesn't leak, then let start()'s outer catch report the failure.
            runCatching { fallback.release() }
            throw e
        }
        Log.i("Compressor", "encoder selected (fallback, default rate control): ${fallback.name}")
        return fallback
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

  // Dolby Vision profile 5 (0x20) carries no HEVC base layer, so no standard
  // Android decoder can render it. Detect it up front so start() can reject the
  // input before allocating the muxer/encoder/EGL surfaces. Profiles 8.x do carry
  // an HEVC base layer and are remapped to HEVC in prepareDecoder.
    private fun isUnsupportedDolbyVision(inputFormat: MediaFormat): Boolean {
        val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: return false
        if (!mime.equals("video/dolby-vision", ignoreCase = true)) return false
        val profile = if (inputFormat.containsKey(MediaFormat.KEY_PROFILE)) {
            inputFormat.getInteger(MediaFormat.KEY_PROFILE)
        } else {
            -1
        }
        return profile == 0x20
    }

  // Function to prepare the video decoder
    private fun prepareDecoder(
        inputFormat: MediaFormat,
        outputSurface: OutputSurface,
    ): MediaCodec {

        // Some inputs (e.g. iPhone .MOV files) report a "video/dolby-vision" MIME
        // type that many devices cannot decode. Remap to a decodable base-layer
        // codec, or fail with a clear error, before creating the decoder (#398).
        ensureDecodableVideoFormat(inputFormat)

        // Clear Dolby Vision specific profile and level to prevent configuration failures
        // when the MIME type has been remapped to AVC/HEVC.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            inputFormat.removeKey(MediaFormat.KEY_PROFILE)
            inputFormat.removeKey(MediaFormat.KEY_LEVEL)
        }

        val decoder = MediaCodec.createDecoderByType(inputFormat.getString(MediaFormat.KEY_MIME)!!)

        decoder.configure(inputFormat, outputSurface.getSurface(), null, 0)

        return decoder
    }

  // Function to release resources.
  // Every call is wrapped in runCatching so a failure in one teardown step
  // does not skip the others (leaking codec handles + GL surfaces). Order:
  // detach extractor → stop+release decoder → stop+release encoder →
  // release input EGL surface → release output surface (joins its
  // HandlerThread). Releasing surfaces last avoids the encoder asking a
  // freed EGL surface for buffers during its own shutdown.
  //
  // decoder / inputSurface / outputSurface are nullable so this also serves the
  // partial-init cleanup path, where a setup failure leaves some handles
  // uncreated. The encoder is always created before teardown is reachable.
    private fun dispose(
        videoIndex: Int,
        decoder: MediaCodec?,
        encoder: MediaCodec,
        inputSurface: InputSurface?,
        outputSurface: OutputSurface?,
        extractor: MediaExtractor
    ) {
        runCatching { extractor.unselectTrack(videoIndex) }
            .onFailure { Log.w("Compressor", "extractor.unselectTrack failed", it) }

        runCatching { decoder?.stop() }
            .onFailure { Log.w("Compressor", "decoder.stop failed", it) }
        runCatching { decoder?.release() }
            .onFailure { Log.w("Compressor", "decoder.release failed", it) }

        runCatching { encoder.stop() }
            .onFailure { Log.w("Compressor", "encoder.stop failed", it) }
        runCatching { encoder.release() }
            .onFailure { Log.w("Compressor", "encoder.release failed", it) }

        runCatching { inputSurface?.release() }
            .onFailure { Log.w("Compressor", "inputSurface.release failed", it) }
        runCatching { outputSurface?.release() }
            .onFailure { Log.w("Compressor", "outputSurface.release failed", it) }
    }
}
