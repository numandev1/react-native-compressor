package com.reactnativecompressor.Video.VideoCompressor.utils

/**
 * Converts a 32-bit unsigned integer to a long.
 * @param int32 The 32-bit unsigned integer to convert.
 * @return The equivalent long value.
 */
fun uInt32ToLong(int32: Int): Long {
  return int32.toLong()
}

/**
 * Converts a long to a 32-bit unsigned integer, throwing an exception if the value is too large.
 * @param uInt32 The long value to convert.
 * @return The equivalent 32-bit unsigned integer.
 * @throws Exception if the provided long value is greater than Int.MAX_VALUE or negative.
 */
fun uInt32ToInt(uInt32: Long): Int {
  if (uInt32 > Int.MAX_VALUE || uInt32 < 0) {
    throw Exception("uInt32 value is too large or negative")
  }
  return uInt32.toInt()
}

/**
 * Converts a 64-bit unsigned integer to a long, throwing an exception if the value is negative.
 * @param uInt64 The 64-bit unsigned integer to convert.
 * @return The equivalent long value.
 * @throws Exception if the provided long value is negative.
 */
fun uInt64ToLong(uInt64: Long): Long {
  if (uInt64 < 0) throw Exception("uInt64 value is negative")
  return uInt64
}

/**
 * Converts a 32-bit unsigned integer to an integer, throwing an exception if the value is negative.
 * @param uInt32 The 32-bit unsigned integer to convert.
 * @return The equivalent integer value.
 * @throws Exception if the provided integer value is negative.
 */
fun uInt32ToInt(uInt32: Int): Int {
  if (uInt32 < 0) {
    throw Exception("uInt32 value is negative")
  }
  return uInt32
}
