package com.reactnativecompressor.Video.VideoCompressor.utils

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Raw MP4 walker that recovers an ISO 6709 GPS string when
 * MediaMetadataRetriever.METADATA_KEY_LOCATION fails to return one.
 *
 * Why this exists: device vendors disagree on where GPS lives.
 *   - Most phones write Apple "©xyz" under moov/udta or moov/trak/udta.
 *   - Some ISO-compliant captures use the standard "loci" box.
 *   - Newer iOS / Android captures use the iTunes-style
 *     moov/meta/keys + moov/meta/ilst pair with the key
 *     "com.apple.quicktime.location.ISO6709".
 *
 * Android's retriever only reads movie-level "©xyz" and silently returns
 * null for everything else. The walker descends through every container
 * atom and tries each known encoding in priority order.
 */
object LocationExtractor {

  // Share the "Compressor" log tag so the atom dump is visible alongside
  // the existing pipeline diagnostics without needing an extra logcat filter.
  private const val TAG = "Compressor"

  private val CONTAINER_TYPES = setOf("moov", "trak", "mdia", "minf", "udta", "meta", "ilst")

  fun extract(context: Context, uri: Uri): String? {
    Log.i(TAG, "LocationExtractor.extract uri=$uri")
    return try {
      openChannel(context, uri)?.use { channel ->
        Log.i(TAG, "LocationExtractor: file size=${channel.size()}")
        val state = WalkState()
        walk(channel, 0L, channel.size(), state, depth = 0)
        val viaBox = chooseBest(state)
        // Log only presence, never the coordinate strings — these are the
        // user's exact GPS values and must not land in production logcat.
        Log.i(
          TAG,
          "LocationExtractor box scan: hasXyz=${!state.xyz.isNullOrEmpty()} hasItunesLocation=${!state.itunesLocation.isNullOrEmpty()} hasLoci=${!state.loci.isNullOrEmpty()} hasChosenLocation=${!viaBox.isNullOrEmpty()}"
        )
        // Samsung phones (Galaxy S10 / Android 12 verified) write GPS into
        // an SEF (Samsung Extended Format) trailer that sits after mdat,
        // outside the standard MP4 box hierarchy. The trailer contains
        // an ISO 6709 string in plain ASCII. Scan the file tail and let
        // the strict regex extract it.
        viaBox ?: scanTrailerForIso6709(channel)
      }
    } catch (e: Exception) {
      Log.w(TAG, "LocationExtractor extract failed", e)
      null
    }
  }

  /**
   * Open a FileChannel for either a content:// URI (via ContentResolver) or
   * a raw filesystem path. JS layer hands the compressor URIs in three
   * shapes — content://, file://, and bare /storage/... paths — and the
   * latter cannot be opened through ContentResolver.
   */
  private fun openChannel(context: Context, uri: Uri): FileChannel? {
    val scheme = uri.scheme
    if (scheme == null || scheme == "file") {
      val path = uri.path ?: uri.toString()
      val file = File(path)
      if (!file.exists()) {
        Log.w(TAG, "LocationExtractor: file does not exist $path")
        return null
      }
      return FileInputStream(file).channel
    }
    val pfd = context.contentResolver.openFileDescriptor(uri, "r")
    if (pfd == null) {
      Log.w(TAG, "LocationExtractor: openFileDescriptor returned null for $uri")
      return null
    }
    // AutoCloseInputStream closes the ParcelFileDescriptor when the stream
    // is closed. A bare FileInputStream over pfd.fileDescriptor would leak
    // the pfd until finalizer runs.
    return ParcelFileDescriptor.AutoCloseInputStream(pfd).channel
  }

  // Strict ISO 6709 pattern: signed lat, signed lon, optional signed alt,
  // mandatory trailing slash. Tight enough that random bytes inside mdat
  // virtually never match, lenient enough to accept the small precision
  // variations vendors use.
  private val ISO6709_REGEX = Regex(
    "[+-]\\d{1,3}\\.\\d{2,7}[+-]\\d{1,3}\\.\\d{2,7}([+-]\\d{1,5}(\\.\\d+)?)?/"
  )

  private fun scanTrailerForIso6709(channel: FileChannel): String? {
    val size = channel.size()
    // 1 MiB tail covers every SEF trailer observed so far. Capped so very
    // small clips do not read past start of file.
    val tailSize = minOf(size, 1L shl 20).toInt()
    if (tailSize <= 0) return null
    val start = size - tailSize
    val buf = ByteBuffer.allocate(tailSize)
    channel.position(start)
    if (channel.read(buf) <= 0) return null
    buf.flip()
    val bytes = ByteArray(buf.remaining())
    buf.get(bytes)
    val text = String(bytes, StandardCharsets.ISO_8859_1)
    val match = ISO6709_REGEX.find(text)
    Log.i(TAG, "LocationExtractor SEF trailer scan matched=${match != null}")
    return match?.value
  }

  private class WalkState {
    var xyz: String? = null
    var loci: String? = null
    var itunesLocation: String? = null
    // iTunes-style meta state.
    val itunesKeys: ArrayList<String> = ArrayList()
    var insideMeta: Boolean = false
  }

  private fun chooseBest(s: WalkState): String? {
    return s.xyz?.takeIf { it.isNotEmpty() }
      ?: s.itunesLocation?.takeIf { it.isNotEmpty() }
      ?: s.loci?.takeIf { it.isNotEmpty() }
  }

  private fun walk(
    channel: FileChannel,
    start: Long,
    end: Long,
    state: WalkState,
    depth: Int,
  ) {
    var pos = start
    val header = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN)
    while (pos + 8 <= end) {
      header.clear()
      header.limit(8)
      channel.position(pos)
      if (channel.read(header) < 8) break
      header.flip()
      val rawSize = header.int.toLong() and 0xFFFFFFFFL
      val typeBytes = ByteArray(4)
      header.get(typeBytes)
      val type = fourCC(typeBytes)

      var headerSize = 8L
      var boxSize = rawSize
      if (rawSize == 1L) {
        val ext = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
        channel.position(pos + 8)
        if (channel.read(ext) < 8) break
        ext.flip()
        boxSize = ext.long
        headerSize = 16L
      } else if (rawSize == 0L) {
        boxSize = end - pos
      }

      if (boxSize < headerSize || pos + boxSize > end) break
      val childEnd = pos + boxSize
      val childStart = pos + headerSize

      if (depth < 5) {
        // Use Log.i so the atom tree appears in default logcat output and
        // can be pasted back when GPS extraction misses a vendor-specific
        // box layout. Depth-bounded to avoid spamming on large mdat chunks.
        Log.i(TAG, "LocationExtractor atom $type @ $pos size=$boxSize depth=$depth")
      }

      when {
        // Apple Quicktime "©xyz" - 0xA9 'x' 'y' 'z'.
        typeBytes[0] == 0xA9.toByte() &&
          typeBytes[1] == 'x'.code.toByte() &&
          typeBytes[2] == 'y'.code.toByte() &&
          typeBytes[3] == 'z'.code.toByte() -> {
          val parsed = readXyz(channel, childStart, childEnd)
          if (!parsed.isNullOrEmpty() && state.xyz == null) {
            state.xyz = parsed
            Log.i(TAG, "found ©xyz")
          }
        }

        // ISO 14496-12 "loci" location box.
        type == "loci" -> {
          val parsed = readLoci(channel, childStart, childEnd)
          if (!parsed.isNullOrEmpty() && state.loci == null) {
            state.loci = parsed
            Log.i(TAG, "found loci")
          }
        }

        // iTunes-style metadata under moov/meta.
        type == "keys" && state.insideMeta -> {
          parseItunesKeys(channel, childStart, childEnd, state)
        }
        type == "ilst" && state.insideMeta -> {
          parseItunesIlst(channel, childStart, childEnd, state)
        }
      }

      if (type in CONTAINER_TYPES) {
        // "meta" has a 4-byte version+flags prefix before its children.
        val innerStart = if (type == "meta") childStart + 4 else childStart
        val priorMeta = state.insideMeta
        if (type == "meta") state.insideMeta = true
        walk(channel, innerStart, childEnd, state, depth + 1)
        state.insideMeta = priorMeta
      }

      pos = childEnd
    }
  }

  private fun fourCC(b: ByteArray): String {
    val sb = StringBuilder(4)
    for (byte in b) {
      val c = byte.toInt() and 0xFF
      sb.append(if (c in 0x20..0x7E) c.toChar() else '?')
    }
    return sb.toString()
  }

  private fun readBoxContent(channel: FileChannel, start: Long, end: Long): ByteBuffer? {
    val len = (end - start).toInt()
    if (len <= 0) return null
    val buf = ByteBuffer.allocate(len).order(ByteOrder.BIG_ENDIAN)
    channel.position(start)
    if (channel.read(buf) < len) return null
    buf.flip()
    return buf
  }

  /**
   * Apple "©xyz" content:
   *   uint16 length
   *   uint16 language code (packed)
   *   bytes  ISO 6709 string
   */
  private fun readXyz(channel: FileChannel, start: Long, end: Long): String? {
    val buf = readBoxContent(channel, start, end) ?: return null
    if (buf.remaining() < 4) return null
    val len = buf.short.toInt() and 0xFFFF
    buf.short
    val take = minOf(len, buf.remaining())
    if (take <= 0) return null
    val bytes = ByteArray(take)
    buf.get(bytes)
    return String(bytes, StandardCharsets.UTF_8).trim().ifEmpty { null }
  }

  /**
   * ISO 14496-12 "loci" content:
   *   uint8  version
   *   uint24 flags
   *   uint16 language
   *   utf8z  name
   *   uint8  role
   *   uint32 longitude (16.16 fixed)
   *   uint32 latitude  (16.16 fixed)
   *   uint32 altitude  (16.16 fixed)
   *   ...
   */
  private fun readLoci(channel: FileChannel, start: Long, end: Long): String? {
    val buf = readBoxContent(channel, start, end) ?: return null
    if (buf.remaining() < 6) return null
    buf.int // version + flags
    buf.short // language
    // Skip null-terminated name.
    while (buf.hasRemaining() && buf.get() != 0.toByte()) { /* skip */ }
    if (buf.remaining() < 1 + 12) return null
    buf.get() // role
    val longitude = fixedPoint1616(buf.int)
    val latitude = fixedPoint1616(buf.int)
    val altitude = fixedPoint1616(buf.int)
    return formatIso6709(latitude, longitude, altitude)
  }

  private fun fixedPoint1616(raw: Int): Double {
    return raw.toDouble() / 65536.0
  }

  private fun formatIso6709(lat: Double, lon: Double, alt: Double): String {
    val sb = StringBuilder()
    sb.append(if (lat >= 0) "+" else "")
    sb.append(String.format(Locale.US, "%.4f", lat))
    sb.append(if (lon >= 0) "+" else "")
    sb.append(String.format(Locale.US, "%.4f", lon))
    if (alt != 0.0) {
      sb.append(if (alt >= 0) "+" else "")
      sb.append(String.format(Locale.US, "%.3f", alt))
    }
    sb.append('/')
    return sb.toString()
  }

  /**
   * Apple iTunes-style "keys" box content:
   *   uint32 version+flags
   *   uint32 entry_count
   *   for each:
   *     uint32 key_size (includes header)
   *     uint32 key_namespace ('mdta')
   *     bytes  key_value (utf-8)
   */
  private fun parseItunesKeys(channel: FileChannel, start: Long, end: Long, state: WalkState) {
    val buf = readBoxContent(channel, start, end) ?: return
    state.itunesKeys.clear()
    if (buf.remaining() < 8) return
    buf.int // version + flags
    val count = buf.int
    for (i in 0 until count) {
      if (buf.remaining() < 8) break
      val entrySize = buf.int
      buf.int // namespace
      val keyLen = entrySize - 8
      if (keyLen <= 0 || keyLen > buf.remaining()) break
      val keyBytes = ByteArray(keyLen)
      buf.get(keyBytes)
      state.itunesKeys.add(String(keyBytes, StandardCharsets.UTF_8))
    }
    Log.i(TAG, "LocationExtractor itunes keys: ${state.itunesKeys}")
  }

  /**
   * Apple iTunes-style "ilst" box. Each child is an indexed item whose
   * type is a uint32 index pointing back into the "keys" table. Inside
   * each item is a "data" sub-box with the actual payload.
   */
  private fun parseItunesIlst(channel: FileChannel, start: Long, end: Long, state: WalkState) {
    var pos = start
    val header = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
    while (pos + 8 <= end) {
      header.clear()
      channel.position(pos)
      if (channel.read(header) < 8) break
      header.flip()
      val itemSize = header.int.toLong() and 0xFFFFFFFFL
      val indexBytes = ByteArray(4)
      header.get(indexBytes)
      val index = ByteBuffer.wrap(indexBytes).order(ByteOrder.BIG_ENDIAN).int
      if (itemSize < 8 || pos + itemSize > end) break
      val itemEnd = pos + itemSize
      val key = state.itunesKeys.getOrNull(index - 1)
      if (key == "com.apple.quicktime.location.ISO6709") {
        val payload = findItunesData(channel, pos + 8, itemEnd)
        if (!payload.isNullOrEmpty() && state.itunesLocation == null) {
          state.itunesLocation = payload
          Log.i(TAG, "found itunes location")
        }
      }
      pos = itemEnd
    }
  }

  private fun findItunesData(channel: FileChannel, start: Long, end: Long): String? {
    var pos = start
    val header = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
    while (pos + 8 <= end) {
      header.clear()
      channel.position(pos)
      if (channel.read(header) < 8) break
      header.flip()
      val size = header.int.toLong() and 0xFFFFFFFFL
      val typeBytes = ByteArray(4)
      header.get(typeBytes)
      val type = fourCC(typeBytes)
      if (size < 8 || pos + size > end) break
      if (type == "data") {
        // data box: uint32 type indicator, uint32 locale, then payload.
        val payloadStart = pos + 8 + 8
        val payloadEnd = pos + size
        if (payloadEnd > payloadStart) {
          val len = (payloadEnd - payloadStart).toInt()
          val buf = ByteBuffer.allocate(len)
          channel.position(payloadStart)
          if (channel.read(buf) >= len) {
            buf.flip()
            val bytes = ByteArray(len)
            buf.get(bytes)
            return String(bytes, StandardCharsets.UTF_8).trim().ifEmpty { null }
          }
        }
      }
      pos += size
    }
    return null
  }
}
