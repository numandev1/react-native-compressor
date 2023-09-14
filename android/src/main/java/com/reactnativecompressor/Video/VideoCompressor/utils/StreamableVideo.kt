package com.reactnativecompressor.Video.VideoCompressor.utils

import android.util.Log
import com.reactnativecompressor.Video.VideoCompressor.data.*
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

object StreamableVideo {

    private const val tag = "StreamableVideo"
    private const val ATOM_PREAMBLE_SIZE = 8

    /**
     * Starts the process of making a video file "fast start" and saves it to the output file.
     *
     * @param inputVideoFile The input video file.
     * @param outputVideoFile The output video file.
     * @return `true` if the operation is successful, `false` if the input file is already fast start.
     * @throws IOException if an I/O error occurs.
     */
    @Throws(IOException::class)
    fun start(`in`: File?, out: File): Boolean {
        var ret = false
        var inStream: FileInputStream? = null
        var outStream: FileOutputStream? = null
        return try {
            inStream = FileInputStream(`in`)
            val infile = inStream.channel
            outStream = FileOutputStream(out)
            val outfile = outStream.channel
            convert(infile, outfile).also { ret = it }
        } finally {
            safeClose(inStream)
            safeClose(outStream)
            if (!ret) {
                out.delete()
            }
        }
    }

  @Throws(IOException::class)
  private fun convert(inputFileChannel: FileChannel, outputFileChannel: FileChannel): Boolean {
    val atomBytes = ByteBuffer.allocate(ATOM_PREAMBLE_SIZE).order(ByteOrder.BIG_ENDIAN)
    var atomType = 0
    var atomSize: Long = 0
    val lastOffset: Long
    val moovAtom: ByteBuffer
    var ftypAtom: ByteBuffer? = null
    var startOffset: Long = 0

    // Traverse through the atoms in the file to ensure that 'moov' is at the end.
    while (readAndFill(inputFileChannel, atomBytes)) {
      atomSize = uInt32ToLong(atomBytes.int)
      atomType = atomBytes.int

      // Keep the 'ftyp' atom.
      if (atomType == FTYP_ATOM) {
        val ftypAtomSize = uInt32ToInt(atomSize)
        ftypAtom = ByteBuffer.allocate(ftypAtomSize).order(ByteOrder.BIG_ENDIAN)
        atomBytes.rewind()
        ftypAtom.put(atomBytes)
        if (inputFileChannel.read(ftypAtom) < ftypAtomSize - ATOM_PREAMBLE_SIZE) break
        ftypAtom.flip()
        startOffset = inputFileChannel.position() // After 'ftyp' atom.
      } else {
        if (atomSize == 1L) {
          /* 64-bit special case */
          atomBytes.clear()
          if (!readAndFill(inputFileChannel, atomBytes)) break
          atomSize = uInt64ToLong(atomBytes.long)
          inputFileChannel.position(inputFileChannel.position() + atomSize - ATOM_PREAMBLE_SIZE * 2) // Seek.
        } else {
          inputFileChannel.position(inputFileChannel.position() + atomSize - ATOM_PREAMBLE_SIZE) // Seek.
        }
      }
      if (atomType != FREE_ATOM
        && atomType != JUNK_ATOM
        && atomType != MDAT_ATOM
        && atomType != MOOV_ATOM
        && atomType != PNOT_ATOM
        && atomType != SKIP_ATOM
        && atomType != WIDE_ATOM
        && atomType != PICT_ATOM
        && atomType != UUID_ATOM
        && atomType != FTYP_ATOM
      ) {
        Log.wtf(tag, "Encountered a non-QT top-level atom (Is this a QuickTime file?)")
        break
      }

      /* The atom header is 8 (or 16 bytes). If the atom size (which
   * includes these 8 or 16 bytes) is less than that, we won't be
   * able to continue scanning sensibly after this atom, so break. */
      if (atomSize < 8) break
    }
    if (atomType != MOOV_ATOM) {
      Log.wtf(tag, "The last atom in the file was not a 'moov' atom")
      return false
    }

    // 'atomSize' is 'uint64', but for 'moov', 'uint32' should be stored.
    val moovAtomSize: Int = uInt32ToInt(atomSize)
    lastOffset =
      inputFileChannel.size() - moovAtomSize
    moovAtom = ByteBuffer.allocate(moovAtomSize).order(ByteOrder.BIG_ENDIAN)
    if (!readAndFill(inputFileChannel, moovAtom, lastOffset)) {
      throw Exception("Failed to read 'moov' atom")
    }

    if (moovAtom.getInt(12) == CMOV_ATOM) {
      throw Exception("This utility does not support compressed 'moov' atoms yet")
    }

    // Crawl through the 'moov' chunk in search of 'stco' or 'co64' atoms.
    while (moovAtom.remaining() >= 8) {
      val atomHead = moovAtom.position()
      atomType = moovAtom.getInt(atomHead + 4)
      if (!(atomType == STCO_ATOM || atomType == CO64_ATOM)) {
        moovAtom.position(moovAtom.position() + 1)
        continue
      }
      atomSize = uInt32ToLong(moovAtom.getInt(atomHead)) // 'uint32'
      if (atomSize > moovAtom.remaining()) {
        throw Exception("Bad atom size")
      }
      // Skip size (4 bytes), type (4 bytes), version (1 byte), and flags (3 bytes).
      moovAtom.position(atomHead + 12)
      if (moovAtom.remaining() < 4) {
        throw Exception("Malformed atom")
      }
      // 'uint32_t', but assuming 'moovAtomSize' is in 'int32' range, so this will be in 'int32' range.
      val offsetCount = uInt32ToInt(moovAtom.int)
      if (atomType == STCO_ATOM) {
        Log.i(tag, "Patching 'stco' atom...")
        if (moovAtom.remaining() < offsetCount * 4) {
          throw Exception("Bad atom size/element count")
        }
        for (i in 0 until offsetCount) {
          val currentOffset = moovAtom.getInt(moovAtom.position())
          val newOffset =
            currentOffset + moovAtomSize // Calculate 'uint32' in 'int', bitwise addition.

          if (currentOffset < 0 && newOffset >= 0) {
            throw Exception(
              "This is a bug in the original 'qt-faststart.c': " +
                "'stco' atom should be extended to 'co64' atom as the new offset value overflows 'uint32', " +
                "but it is not implemented."
            )
          }
          moovAtom.putInt(newOffset)
        }
      } else if (atomType == CO64_ATOM) {
        Log.wtf(tag, "Patching 'co64' atom...")
        if (moovAtom.remaining() < offsetCount * 8) {
          throw Exception("Bad atom size/element count")
        }
        for (i in 0 until offsetCount) {
          val currentOffset = moovAtom.getLong(moovAtom.position())
          moovAtom.putLong(currentOffset + moovAtomSize) // Calculate 'uint64' in 'long', bitwise addition.
        }
      }
    }
    inputFileChannel.position(startOffset) // Seek after 'ftyp' atom.
    if (ftypAtom != null) {
      // Dump the same 'ftyp' atom.
      Log.i(tag, "Writing 'ftyp' atom...")
      ftypAtom.rewind()
      outputFileChannel.write(ftypAtom)
    }

    // Dump the new 'moov' atom.
    Log.i(tag, "Writing 'moov' atom...")
    moovAtom.rewind()
    outputFileChannel.write(moovAtom)

    // Copy the remainder of the input file, from offset 0 -> (lastOffset - startOffset) - 1.
    Log.i(tag, "Copying the rest of the file...")
    inputFileChannel.transferTo(startOffset, lastOffset - startOffset, outputFileChannel)
    return true
  }

  private fun safeClose(closeable: Closeable?) {
    if (closeable != null) {
      try {
        closeable.close()
      } catch (e: IOException) {
        Log.wtf(tag, "Failed to close file: ")
      }
    }
  }

  @Throws(IOException::class)
  private fun readAndFill(inputFileChannel: FileChannel, buffer: ByteBuffer): Boolean {
    buffer.clear()
    val size = inputFileChannel.read(buffer)
    buffer.flip()
    return size == buffer.capacity()
  }

  @Throws(IOException::class)
  private fun readAndFill(inputFileChannel: FileChannel, buffer: ByteBuffer, position: Long): Boolean {
    buffer.clear()
    val size = inputFileChannel.read(buffer, position)
    buffer.flip()
    return size == buffer.capacity()
  }
}
