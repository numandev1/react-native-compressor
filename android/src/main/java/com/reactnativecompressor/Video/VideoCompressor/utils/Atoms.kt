package com.reactnativecompressor.Video.VideoCompressor.data

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Converts a four-character code (FOURCC) represented as a byte array into an integer.
 * FOURCC is an identifier for video codecs, compression formats, and color/pixel formats in media files.
 * Each FOURCC code is a 32-bit value stored in 4 bytes.
 */
fun fourCcToInt(byteArray: ByteArray): Int {
  // The bytes in the byteArray are ordered from most significant to least significant.
  return ByteBuffer.wrap(byteArray).order(ByteOrder.BIG_ENDIAN).int
}

// Constants for commonly used FOURCC codes in media file formats:

// Unused space available in the file.
val FREE_ATOM = fourCcToInt(byteArrayOf('f'.code.toByte(), 'r'.code.toByte(), 'e'.code.toByte(), 'e'.code.toByte()))

// Junk data in the file.
val JUNK_ATOM = fourCcToInt(byteArrayOf('j'.code.toByte(), 'u'.code.toByte(), 'n'.code.toByte(), 'k'.code.toByte()))

// Media data containing samples like video frames and audio groups.
val MDAT_ATOM = fourCcToInt(byteArrayOf('m'.code.toByte(), 'd'.code.toByte(), 'a'.code.toByte(), 't'.code.toByte()))

// Metadata about the movie, including the number and type of tracks and sample data location.
val MOOV_ATOM = fourCcToInt(byteArrayOf('m'.code.toByte(), 'o'.code.toByte(), 'o'.code.toByte(), 'v'.code.toByte()))

// Reference to movie preview data.
val PNOT_ATOM = fourCcToInt(byteArrayOf('p'.code.toByte(), 'n'.code.toByte(), 'o'.code.toByte(), 't'.code.toByte()))

// Unused space available in the file.
val SKIP_ATOM = fourCcToInt(byteArrayOf('s'.code.toByte(), 'k'.code.toByte(), 'i'.code.toByte(), 'p'.code.toByte()))

// Reserved space that can be overwritten by an extended size field.
val WIDE_ATOM = fourCcToInt(byteArrayOf('w'.code.toByte(), 'i'.code.toByte(), 'd'.code.toByte(), 'e'.code.toByte()))

// Picture atom for graphics data.
val PICT_ATOM = fourCcToInt(byteArrayOf('P'.code.toByte(), 'I'.code.toByte(), 'C'.code.toByte(), 'T'.code.toByte()))

// File type compatibility atom that identifies the file type.
val FTYP_ATOM = fourCcToInt(byteArrayOf('f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte()))

// Universally unique identifier atom.
val UUID_ATOM = fourCcToInt(byteArrayOf('u'.code.toByte(), 'u'.code.toByte(), 'i'.code.toByte(), 'd'.code.toByte()))

// Compressed movie atom.
val CMOV_ATOM = fourCcToInt(byteArrayOf('c'.code.toByte(), 'm'.code.toByte(), 'o'.code.toByte(), 'v'.code.toByte()))

// Sample table chunk offset atom.
val STCO_ATOM = fourCcToInt(byteArrayOf('s'.code.toByte(), 't'.code.toByte(), 'c'.code.toByte(), 'o'.code.toByte()))

// 64-bit chunk offset atom.
val CO64_ATOM = fourCcToInt(byteArrayOf('c'.code.toByte(), 'o'.code.toByte(), '6'.code.toByte(), '4'.code.toByte()))
