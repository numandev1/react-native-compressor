package com.reactnativecompressor.Image

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableMap
import com.reactnativecompressor.Utils.MediaCache
import com.reactnativecompressor.Utils.Utils
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream

class ImageMain(private val reactContext: ReactApplicationContext) {
  fun image_compress(
    imagePath: String,
    optionMap: ReadableMap,
    promise: Promise
  ) {
    var imagePath: String? = imagePath
    try {
      val options = ImageCompressorOptions.fromMap(optionMap)
      imagePath = Utils.getRealPath(imagePath, reactContext, options.uuid, options.progressDivider)
      if (options.compressionMethod === ImageCompressorOptions.CompressionMethod.auto) {
        val returnableResult = ImageCompressor.autoCompressImage(imagePath, options, reactContext)
        promise.resolve(returnableResult)
      } else {
        val returnableResult = ImageCompressor.manualCompressImage(imagePath, options, reactContext)
        promise.resolve(returnableResult)
      }
      MediaCache.removeCompletedImagePath(imagePath)
    } catch (ex: Exception) {
      promise.reject(ex)
    }
  }

  fun getImageMetaData(filePath: String, promise: Promise) {
    var filePath: String? = filePath
    try {
      filePath = Utils.getRealPath(filePath, reactContext)
      val uri = Uri.parse(filePath)
      val srcPath = uri.path
      if (srcPath == null) {
        promise.reject("INVALID_PATH", "Could not resolve a file path from $filePath")
        return
      }
      val params = Arguments.createMap()
      val file = File(srcPath)
      val extension = filePath!!.substring(filePath.lastIndexOf(".") + 1)
      params.putDouble("size", file.length().toDouble())
      params.putString("extension", extension)
      params.putString("fileName", file.name)

      // Bounds-only header read: populates outWidth/outHeight/outMimeType without allocating a
      // bitmap, so a 100 MP image costs nothing. These are the true decoder dimensions — reading
      // them from the EXIF tags is what used to return NaN for images with no EXIF block.
      val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      BitmapFactory.decodeFile(srcPath, bounds)
      if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        promise.reject("INVALID_IMAGE", "Failed to read image dimensions from $srcPath")
        return
      }
      val mimeType = canonicalMimeType(bounds.outMimeType) ?: mimeTypeFromExtension(extension)
      params.putInt("width", bounds.outWidth)
      params.putInt("height", bounds.outHeight)
      params.putString("mimeType", mimeType)
      params.putBoolean("hasAlpha", mimeTypeMayHaveAlpha(mimeType))
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        bounds.outColorSpace?.let { params.putString("colorSpace", it.name) }
      }

      // A missing or unreadable EXIF block is normal (PNGs, screenshots, edited images) and must
      // not fail the call — everything above is already enough to resolve with.
      try {
        readExif(srcPath, bounds, params)
      } catch (e: Exception) {
        putOrientation(params, ExifInterface.ORIENTATION_NORMAL, bounds)
      }

      readAnimation(file, mimeType, params)
      promise.resolve(params)
    } catch (e: Exception) {
      promise.reject(e)
    }
  }

  private fun readExif(srcPath: String, bounds: BitmapFactory.Options, params: WritableMap) {
    val exif = ExifInterface(srcPath)
    putOrientation(params, exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL), bounds)

    exif.latLong?.let {
      params.putDouble("latitude", it[0])
      params.putDouble("longitude", it[1])
    }
    val altitude = exif.getAltitude(Double.NaN)
    if (!altitude.isNaN()) params.putDouble("altitude", altitude)

    exif.getAttribute(ExifInterface.TAG_MAKE)?.let { params.putString("make", it) }
    exif.getAttribute(ExifInterface.TAG_MODEL)?.let { params.putString("model", it) }
    exif.getAttribute(ExifInterface.TAG_LENS_MODEL)?.let { params.putString("lensModel", it) }
    exif.getAttribute(ExifInterface.TAG_SOFTWARE)?.let { params.putString("software", it) }

    exif.positiveInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)?.let { params.putInt("iso", it) }
    // Stored as a rational such as "1/60"; getAttributeDouble resolves it to seconds.
    exif.positiveDouble(ExifInterface.TAG_EXPOSURE_TIME)?.let { params.putDouble("exposureTime", it) }
    exif.positiveDouble(ExifInterface.TAG_FOCAL_LENGTH)?.let { params.putDouble("focalLength", it) }
    // Bit 0 of the flash tag is "flash fired"; the remaining bits describe the mode.
    exif.getAttribute(ExifInterface.TAG_FLASH)?.toIntOrNull()?.let { params.putBoolean("flash", it and 0x1 == 1) }
    // "8 8 8" for a 24-bit RGB image — the per-channel depth is the first component.
    exif.getAttribute(ExifInterface.TAG_BITS_PER_SAMPLE)?.split(" ", ",")?.firstOrNull()?.toIntOrNull()
      ?.let { params.putInt("bitDepth", it) }

    // X/YResolution are per ResolutionUnit: 2 = inches (already DPI), 3 = centimetres.
    val perCentimetre = exif.getAttributeInt(ExifInterface.TAG_RESOLUTION_UNIT, 2) == 3
    val toDpi = { value: Double -> if (perCentimetre) value * 2.54 else value }
    exif.positiveDouble(ExifInterface.TAG_X_RESOLUTION)?.let { params.putDouble("dpiX", toDpi(it)) }
    exif.positiveDouble(ExifInterface.TAG_Y_RESOLUTION)?.let { params.putDouble("dpiY", toDpi(it)) }
  }

  /**
   * Walks the container's block/chunk headers to count frames for the two animated formats Android
   * supports. This never decodes pixel data — only the structural bytes are read.
   */
  private fun readAnimation(file: File, mimeType: String, params: WritableMap) {
    val frameCount = try {
      when (mimeType) {
        "image/gif" -> countGifFrames(file)
        "image/webp" -> countWebPFrames(file)
        else -> null
      }
    } catch (e: Exception) {
      null
    } ?: return
    params.putInt("frameCount", frameCount)
    params.putBoolean("isAnimated", frameCount > 1)
  }

  companion object {
    private fun putOrientation(params: WritableMap, rawOrientation: Int, bounds: BitmapFactory.Options) {
      // Orientation 0 is not a legal EXIF value; treat it as "unrotated" like a missing tag.
      val orientation = if (rawOrientation in 1..8) rawOrientation else ExifInterface.ORIENTATION_NORMAL
      val rotation = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_180, ExifInterface.ORIENTATION_FLIP_VERTICAL -> 180
        ExifInterface.ORIENTATION_TRANSPOSE, ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_TRANSVERSE, ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
      }
      val isMirrored = orientation == ExifInterface.ORIENTATION_FLIP_HORIZONTAL ||
        orientation == ExifInterface.ORIENTATION_FLIP_VERTICAL ||
        orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
        orientation == ExifInterface.ORIENTATION_TRANSVERSE
      // Orientations 5-8 are the quarter turns, and only those swap the display axes.
      val swapsAxes = orientation >= 5
      params.putInt("orientation", orientation)
      params.putInt("rotation", rotation)
      params.putBoolean("isMirrored", isMirrored)
      params.putInt("displayWidth", if (swapsAxes) bounds.outHeight else bounds.outWidth)
      params.putInt("displayHeight", if (swapsAxes) bounds.outWidth else bounds.outHeight)
    }

    private fun ExifInterface.positiveInt(tag: String): Int? =
      getAttributeInt(tag, -1).takeIf { it > 0 }

    private fun ExifInterface.positiveDouble(tag: String): Double? =
      getAttributeDouble(tag, -1.0).takeIf { it > 0 }

    /**
     * The decoder's reported type, folded onto the same canonical values iOS returns so the field
     * matches across platforms. Returns null when nothing was detected, so the caller can fall back
     * to the file extension.
     */
    private fun canonicalMimeType(detected: String?): String? = when (detected?.lowercase()) {
      null -> null
      "image/heic", "image/heif", "image/avci" -> "image/heif"
      "image/jpg" -> "image/jpeg"
      "image/tif" -> "image/tiff"
      else -> detected.lowercase()
    }

    private fun mimeTypeFromExtension(extension: String): String = when (extension.lowercase()) {
      "jpg", "jpeg" -> "image/jpeg"
      "png" -> "image/png"
      "gif" -> "image/gif"
      "webp" -> "image/webp"
      "heic", "heif" -> "image/heif"
      "bmp" -> "image/bmp"
      "tif", "tiff" -> "image/tiff"
      "dng" -> "image/x-adobe-dng"
      else -> "image/${extension.lowercase()}"
    }

    /**
     * Inferred from the container, not read exactly: these formats can carry an alpha channel, but
     * confirming that a given file actually uses it would mean decoding the bitmap.
     */
    private fun mimeTypeMayHaveAlpha(mimeType: String): Boolean =
      mimeType == "image/png" || mimeType == "image/webp" || mimeType == "image/gif"

    private fun countGifFrames(file: File): Int? {
      DataInputStream(BufferedInputStream(FileInputStream(file))).use { stream ->
        val header = ByteArray(6)
        stream.readFully(header)
        if (String(header, Charsets.US_ASCII).let { it != "GIF87a" && it != "GIF89a" }) return null
        val packed = ByteArray(7).also { stream.readFully(it) }[4].toInt()
        if (packed and 0x80 != 0) stream.skipFully(3 * (1 shl ((packed and 0x07) + 1)))

        var frames = 0
        while (true) {
          when (stream.read()) {
            0x21 -> { // Extension: a label byte followed by data sub-blocks.
              stream.read()
              stream.skipSubBlocks()
            }
            0x2C -> { // Image descriptor: the only block that introduces a frame.
              val descriptor = ByteArray(9).also { stream.readFully(it) }[8].toInt()
              if (descriptor and 0x80 != 0) stream.skipFully(3 * (1 shl ((descriptor and 0x07) + 1)))
              stream.read() // LZW minimum code size
              stream.skipSubBlocks()
              frames++
            }
            else -> return frames // 0x3B trailer, EOF (-1), or a malformed byte.
          }
        }
      }
    }

    private fun countWebPFrames(file: File): Int? {
      DataInputStream(BufferedInputStream(FileInputStream(file))).use { stream ->
        val riff = ByteArray(12).also { stream.readFully(it) }
        if (String(riff, 0, 4, Charsets.US_ASCII) != "RIFF" || String(riff, 8, 4, Charsets.US_ASCII) != "WEBP") {
          return null
        }
        var frames = 0
        val chunkHeader = ByteArray(8)
        while (true) {
          var read = 0
          while (read < 8) {
            val count = stream.read(chunkHeader, read, 8 - read)
            if (count < 0) return if (frames > 0) frames else 1
            read += count
          }
          val fourCC = String(chunkHeader, 0, 4, Charsets.US_ASCII)
          // RIFF chunk sizes are little-endian and payloads are padded to an even length.
          var size = 0L
          for (i in 0 until 4) size = size or ((chunkHeader[4 + i].toLong() and 0xFF) shl (8 * i))
          if (fourCC == "ANMF") frames++
          stream.skipFully(size + (size and 1L))
        }
      }
    }

    private fun DataInputStream.skipSubBlocks() {
      while (true) {
        val size = read()
        if (size <= 0) return
        skipFully(size.toLong())
      }
    }

    /** `InputStream.skip` may skip fewer bytes than asked; loop until the whole span is consumed. */
    private fun DataInputStream.skipFully(count: Long) {
      var remaining = count
      while (remaining > 0) {
        val skipped = skip(remaining)
        if (skipped <= 0) {
          if (read() < 0) return
          remaining--
        } else {
          remaining -= skipped
        }
      }
    }

    private fun DataInputStream.skipFully(count: Int) = skipFully(count.toLong())
  }
}
