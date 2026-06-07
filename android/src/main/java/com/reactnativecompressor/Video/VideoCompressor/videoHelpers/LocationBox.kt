package com.reactnativecompressor.Video.VideoCompressor.video

import org.mp4parser.support.AbstractBox
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/**
 * Apple Quicktime "©xyz" box that stores an ISO 6709 location string
 * (e.g. "+37.4220-122.0840/" or "+37.4220-122.0840+009.000/").
 *
 * Android's MediaMetadataRetriever.METADATA_KEY_LOCATION reads this exact
 * box; writing it preserves GPS metadata across the compression rewrite.
 *
 * Layout of the box content:
 *   uint16 BE  text byte length
 *   uint16 BE  language packed code (0x15c7 = "und")
 *   bytes      ISO 6709 string (no NUL terminator)
 */
class LocationBox : AbstractBox(TYPE) {

  var location: String = ""

  override fun getContentSize(): Long {
    val bytes = location.toByteArray(StandardCharsets.UTF_8)
    return (2 + 2 + bytes.size).toLong()
  }

  override fun _parseDetails(content: ByteBuffer) {
    val len = content.short.toInt() and 0xFFFF
    content.short
    val bytes = ByteArray(len)
    content.get(bytes)
    location = String(bytes, StandardCharsets.UTF_8)
  }

  override fun getContent(byteBuffer: ByteBuffer) {
    val bytes = location.toByteArray(StandardCharsets.UTF_8)
    byteBuffer.putShort(bytes.size.toShort())
    byteBuffer.putShort(LANG_UND)
    byteBuffer.put(bytes)
  }

  companion object {
    const val TYPE = "©xyz"
    private const val LANG_UND: Short = 0x15c7
  }
}
