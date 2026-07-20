package com.reactnativecompressor.Audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.reactnativecompressor.Utils.Utils.addLog
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Decodes any audio container/codec Android can play (m4a/AAC, mp3, ogg,
 * flac, ...) into a 16-bit PCM little-endian WAV file that LAME can consume.
 *
 * LAME's WaveReader only accepts PCM 16-bit LE WAV, so for any compressed
 * (or non-matching) input we must transcode through a real decoder — jlayer's
 * Converter is MP3-only and silently fails for m4a, ogg, flac, etc.
 */
internal object AudioTranscoder {

  private const val TIMEOUT_US = 10_000L
  private const val WAV_HEADER_SIZE = 44

  /**
   * Decode [srcPath] into a 16-bit PCM WAV at [dstPath].
   *
   * @throws IOException if the file cannot be opened, has no audio track,
   *   cannot be decoded on this device, or the output cannot be written.
   */
  @Throws(IOException::class)
  fun decodeToWav(srcPath: String, dstPath: String) {
    addLog("decodeToWav: $srcPath -> $dstPath")

    val extractor = MediaExtractor()
    var decoder: MediaCodec? = null
    var output: BufferedOutputStream? = null

    try {
      // -- setup extractor -------------------------------------------------
      extractor.setDataSource(srcPath)

      val audioTrackIndex = findAudioTrackIndex(extractor)
        ?: throw IOException("No audio track found in $srcPath")
      extractor.selectTrack(audioTrackIndex)

      val inputFormat = extractor.getTrackFormat(audioTrackIndex)
      val mime = inputFormat.getString(MediaFormat.KEY_MIME)
        ?: throw IOException("Audio track has no mime in $srcPath")
      val sampleRate = if (inputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
        inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
      } else {
        throw IOException("Audio track has no sample rate in $srcPath")
      }
      val channelCount = if (inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
        inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
      } else {
        throw IOException("Audio track has no channel count in $srcPath")
      }

      // -- create + configure decoder ---------------------------------------
      try {
        decoder = MediaCodec.createDecoderByType(mime)
      } catch (e: IllegalArgumentException) {
        throw IOException("Device cannot decode audio mime $mime", e)
      } catch (e: Exception) {
        throw IOException("Failed to create decoder for $mime", e)
      }
      decoder!!.configure(inputFormat, null, null, 0)
      decoder!!.start()

      // Capture a non-null val so Kotlin smart-cast works in the loop below.
      val d = decoder!!

      // -- prepare output WAV file ------------------------------------------
      val outFile = File(dstPath)
      outFile.parentFile?.mkdirs()
      output = BufferedOutputStream(FileOutputStream(outFile))
      // Reserve 44 bytes for the RIFF/WAVE header; we'll come back and
      // overwrite with the correct sizes once we know how many PCM bytes
      // were produced.
      output.write(ByteArray(WAV_HEADER_SIZE))

      var totalBytesWritten = 0L
      val info = MediaCodec.BufferInfo()
      var inputDone = false
      var outputDone = false

      // -- decode loop ------------------------------------------------------
      while (!outputDone) {
        // Feed compressed samples into the decoder.
        if (!inputDone) {
          val inIndex = d.dequeueInputBuffer(TIMEOUT_US)
          if (inIndex >= 0) {
            val inBuf = d.getInputBuffer(inIndex)
              ?: throw IOException("Decoder returned null input buffer")
            inBuf.clear()
            val sampleSize = extractor.readSampleData(inBuf, 0)
            if (sampleSize < 0) {
              d.queueInputBuffer(
                inIndex, 0, 0, 0,
                MediaCodec.BUFFER_FLAG_END_OF_STREAM
              )
              inputDone = true
            } else {
              d.queueInputBuffer(
                inIndex, 0, sampleSize, extractor.sampleTime, 0
              )
              extractor.advance()
            }
          }
        }

        // Drain decoded PCM and append it to the file (after the header
        // placeholder).
        val outIndex = d.dequeueOutputBuffer(info, TIMEOUT_US)
        if (outIndex >= 0) {
          val outBuf = d.getOutputBuffer(outIndex)
            ?: throw IOException("Decoder returned null output buffer")
          if (info.size > 0) {
            // outBuf is a direct ByteBuffer; data is laid out in native byte
            // order in [info.offset, info.offset + info.size). Copy the whole
            // run in a single contiguous read.
            val copy = ByteArray(info.size)
            outBuf.position(info.offset)
            outBuf.get(copy)
            output.write(copy)
            totalBytesWritten += info.size
          }
          d.releaseOutputBuffer(outIndex, false)

          if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
            outputDone = true
          }
        } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
          // The final decoded format may differ from what we read at
          // configure time (e.g. AAC decoder may change channel layout
          // after the first frame). Log it for diagnostics; the header we
          // write at the end will use the configure-time values, which is
          // the standard PCM layout that LAME expects.
          addLog("decoder output format changed: ${d.outputFormat}")
        }
      }

      output.flush()

      // Patch the WAV header with the final sizes now that we know them.
      writeWavHeader(outFile, sampleRate, channelCount, totalBytesWritten)

      addLog("decodeToWav: wrote $totalBytesWritten PCM bytes")
    } finally {
      runCatching { decoder?.stop() }
      runCatching { decoder?.release() }
      runCatching { extractor.release() }
      runCatching { output?.close() }
    }
  }

  private fun findAudioTrackIndex(extractor: MediaExtractor): Int? {
    for (i in 0 until extractor.trackCount) {
      val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
      if (mime != null && mime.startsWith("audio/")) return i
    }
    return null
  }

  /**
   * Write a standard 44-byte PCM 16-bit little-endian WAV header into the
   * first [WAV_HEADER_SIZE] bytes of [outFile], anchored to the [dataBytes]
   * sample body that follows.
   */
  private fun writeWavHeader(outFile: File, sampleRate: Int, channels: Int, dataBytes: Long) {
    val header = ByteBuffer.allocate(WAV_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
    val byteRate = sampleRate * channels * 2 // 16-bit = 2 bytes per sample
    val blockAlign = (channels * 2).toShort()
    val chunkSize = 36L + dataBytes

    header.put("RIFF".toByteArray(Charsets.US_ASCII))
    header.putInt(chunkSize.toInt())
    header.put("WAVE".toByteArray(Charsets.US_ASCII))
    header.put("fmt ".toByteArray(Charsets.US_ASCII))
    header.putInt(16) // PCM fmt chunk size
    header.putShort(1) // PCM format
    header.putShort(channels.toShort())
    header.putInt(sampleRate)
    header.putInt(byteRate)
    header.putShort(blockAlign)
    header.putShort(16) // bits per sample
    header.put("data".toByteArray(Charsets.US_ASCII))
    header.putInt(dataBytes.toInt())

    val raf = RandomAccessFile(outFile, "rw")
    try {
      raf.seek(0)
      raf.write(header.array(), 0, WAV_HEADER_SIZE)
    } finally {
      raf.close()
    }
  }

}
