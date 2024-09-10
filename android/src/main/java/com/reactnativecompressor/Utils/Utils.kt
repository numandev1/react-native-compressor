package com.reactnativecompressor.Utils

import android.content.ContentResolver
import android.content.res.AssetFileDescriptor
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.reactnativecompressor.Audio.AudioCompressor
import com.reactnativecompressor.Video.VideoCompressor.CompressionListener
import com.reactnativecompressor.Video.VideoCompressor.VideoCompressorClass
import java.io.FileNotFoundException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.util.UUID
import java.util.regex.Pattern


object Utils {
    private const val TAG = "react-native-compessor"
    var compressorExports: MutableMap<String, VideoCompressorClass?> = HashMap()
    @JvmStatic
    fun generateCacheFilePath(extension: String, reactContext: ReactApplicationContext): String {
        val outputDir = reactContext.cacheDir
        return String.format("%s/%s.$extension", outputDir.path, UUID.randomUUID().toString())
    }

    @JvmStatic
    fun compressVideo(srcPath: String, destinationPath: String, resultWidth: Int, resultHeight: Int, videoBitRate: Float, uuid: String,progressDivider:Int, promise: Promise, reactContext: ReactApplicationContext) {
      val currentVideoCompression = intArrayOf(0)
      val videoCompressorClass: VideoCompressorClass? = VideoCompressorClass(reactContext);
      compressorExports[uuid] = videoCompressorClass
      videoCompressorClass?.start(
        srcPath, destinationPath, resultWidth, resultHeight, videoBitRate.toInt(),
        listener = object : CompressionListener {
          override fun onProgress(index: Int, percent: Float) {
            if (percent <= 100) {
              val roundProgress = Math.round(percent)
              if (progressDivider == 0 || (roundProgress % progressDivider == 0 && roundProgress > currentVideoCompression[0])) {
                EventEmitterHandler.emitVideoCompressProgress((percent / 100).toDouble(), uuid)
                currentVideoCompression[0] = roundProgress
              }
            }
          }

          override fun onStart(index: Int) {

          }

          override fun onSuccess(index: Int, size: Long, path: String?) {
            val fileUrl = "file://$destinationPath"
            //convert finish,result(true is success,false is fail)
            promise.resolve(fileUrl)
            MediaCache.removeCompletedImagePath(fileUrl)
            currentVideoCompression[0] = 0
            compressorExports[uuid] = null
          }

            override fun onFailure(index: Int, failureMessage: String) {
              Log.wtf("failureMessage", failureMessage)
              promise.reject(Throwable(failureMessage))
              currentVideoCompression[0] = 0
            }

            override fun onCancelled(index: Int) {
              Log.wtf("TAG", "compression has been cancelled")
              promise.reject(Throwable("compression has been cancelled"))
              // make UI changes, cleanup, etc
              currentVideoCompression[0] = 0
            }
          },
        )
    }

    fun cancelCompressionHelper(uuid: String) {
        try {
            val export = compressorExports[uuid]
            export?.cancel()
          compressorExports[uuid]=null
        } catch (ex: Exception) {
        }
    }

    @JvmStatic
    fun getFileSizeFromURL(urlString: String?): Int {
        val url: URL
        var conn: HttpURLConnection? = null
        return try {
            url = URL(urlString)
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "HEAD"
            conn!!.inputStream
            conn.contentLength
        } catch (e: IOException) {
            throw RuntimeException(e)
        } finally {
            conn?.disconnect()
        }
    }

    @JvmStatic
    fun getRealPath(fileUrl: String?, reactContext: ReactApplicationContext, vararg args: Any?): String? {
        var fileUrl = fileUrl
        if (fileUrl!!.startsWith("content://")) {
            try {
                val uri = Uri.parse(fileUrl)
                fileUrl = RealPathUtil.getRealPath(reactContext, uri)
            } catch (ex: Exception) {
                Log.d(TAG, " Please see this issue: https://github.com/numandev1/react-native-compressor/issues/25")
            }
        } else if (fileUrl.startsWith("http://") || fileUrl.startsWith("https://")) {
            val uuid: String = if (args.size > 0) args[0].toString() else ""
            val progressDivider: Int = if (args.size > 1) args[1] as Int else 0
            fileUrl = Downloader.downloadMediaWithProgress(fileUrl, uuid,progressDivider, reactContext)
            Log.d(TAG, "getRealPath: $fileUrl")
        }
        return slashifyFilePath(fileUrl)
    }



  fun getFileSize(filePath: String, promise: Promise, reactContext:ReactApplicationContext) {
    var filePath: String? = filePath
    if (filePath!!.startsWith("http://") || filePath.startsWith("https://")) {
      promise.resolve(getFileSizeFromURL(filePath))
    } else {
      filePath = getRealPath(filePath, reactContext)
      val uri = Uri.parse(filePath)
      val contentResolver = reactContext.contentResolver
      val fileSize = getLength(uri, contentResolver)
      if (fileSize >= 0) {
        promise.resolve(fileSize.toString())
      } else {
        promise.resolve("")
      }
    }
  }

  fun slashifyFilePath(path: String?): String? {
    return if (path == null) {
      null
    } else if (path.startsWith("file:///")) {
      path
    }  else if (path.startsWith("/")) {
      path.replaceFirst("^/+".toRegex(), "file:///")
    }else {
      // Ensure leading schema with a triple slash
      Pattern.compile("^file:/*").matcher(path).replaceAll("file:///")
    }
  }

  fun addLog(log: String) {
    Log.d(AudioCompressor.TAG,  log)
  }

  val exifAttributes = arrayOf(
    "FNumber",
    "ApertureValue",
    "Artist",
    "BitsPerSample",
    "BrightnessValue",
    "CFAPattern",
    "ColorSpace",
    "ComponentsConfiguration",
    "CompressedBitsPerPixel",
    "Compression",
    "Contrast",
    "Copyright",
    "CustomRendered",
    "DateTime",
    "DateTimeDigitized",
    "DateTimeOriginal",
    "DefaultCropSize",
    "DeviceSettingDescription",
    "DigitalZoomRatio",
    "DNGVersion",
    "ExifVersion",
    "ExposureBiasValue",
    "ExposureIndex",
    "ExposureMode",
    "ExposureProgram",
    "ExposureTime",
    "FileSource",
    "Flash",
    "FlashpixVersion",
    "FlashEnergy",
    "FocalLength",
    "FocalLengthIn35mmFilm",
    "FocalPlaneResolutionUnit",
    "FocalPlaneXResolution",
    "FocalPlaneYResolution",
    "FNumber",
    "GainControl",
    "GPSAltitude",
    "GPSAltitudeRef",
    "GPSAreaInformation",
    "GPSDateStamp",
    "GPSDestBearing",
    "GPSDestBearingRef",
    "GPSDestDistance",
    "GPSDestDistanceRef",
    "GPSDestLatitude",
    "GPSDestLatitudeRef",
    "GPSDestLongitude",
    "GPSDestLongitudeRef",
    "GPSDifferential",
    "GPSDOP",
    "GPSImgDirection",
    "GPSImgDirectionRef",
    "GPSLatitude",
    "GPSLatitudeRef",
    "GPSLongitude",
    "GPSLongitudeRef",
    "GPSMapDatum",
    "GPSMeasureMode",
    "GPSProcessingMethod",
    "GPSSatellites",
    "GPSSpeed",
    "GPSSpeedRef",
    "GPSStatus",
    "GPSTimeStamp",
    "GPSTrack",
    "GPSTrackRef",
    "GPSVersionID",
    "ImageDescription",
    "ImageLength",
    "ImageUniqueID",
    "ImageWidth",
    "InteroperabilityIndex",
    "ISOSpeedRatings",
    "ISOSpeedRatings",
    "JPEGInterchangeFormat",
    "JPEGInterchangeFormatLength",
    "LightSource",
    "Make",
    "MakerNote",
    "MaxApertureValue",
    "MeteringMode",
    "Model",
    "NewSubfileType",
    "OECF",
    "AspectFrame",
    "PreviewImageLength",
    "PreviewImageStart",
    "ThumbnailImage",
    "Orientation",
    "PhotometricInterpretation",
    "PixelXDimension",
    "PixelYDimension",
    "PlanarConfiguration",
    "PrimaryChromaticities",
    "ReferenceBlackWhite",
    "RelatedSoundFile",
    "ResolutionUnit",
    "RowsPerStrip",
    "ISO",
    "JpgFromRaw",
    "SensorBottomBorder",
    "SensorLeftBorder",
    "SensorRightBorder",
    "SensorTopBorder",
    "SamplesPerPixel",
    "Saturation",
    "SceneCaptureType",
    "SceneType",
    "SensingMethod",
    "Sharpness",
    "ShutterSpeedValue",
    "Software",
    "SpatialFrequencyResponse",
    "SpectralSensitivity",
    "StripByteCounts",
    "StripOffsets",
    "SubfileType",
    "SubjectArea",
    "SubjectDistance",
    "SubjectDistanceRange",
    "SubjectLocation",
    "SubSecTime",
    "SubSecTimeDigitized",
    "SubSecTimeDigitized",
    "SubSecTimeOriginal",
    "SubSecTimeOriginal",
    "ThumbnailImageLength",
    "ThumbnailImageWidth",
    "TransferFunction",
    "UserComment",
    "WhiteBalance",
    "WhitePoint",
    "XResolution",
    "YCbCrCoefficients",
    "YCbCrPositioning",
    "YCbCrSubSampling",
    "YResolution"
  )

  fun getLength(uri: Uri, contentResolver: ContentResolver): Long {
    var assetFileDescriptor: AssetFileDescriptor? = null
    try {
      assetFileDescriptor = contentResolver.openAssetFileDescriptor(uri, "r")
    } catch (e: FileNotFoundException) {
      // Do nothing
    }
    val length = assetFileDescriptor?.length ?: -1L
    if (length != -1L) {
      return length
    }
    return if (ContentResolver.SCHEME_CONTENT == uri.scheme) {
      val cursor = contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
      if (cursor != null) {
        try {
          val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
          if (sizeIndex != -1 && cursor.moveToFirst()) {
            return try {
              cursor.getLong(sizeIndex)
            } catch (ignored: Throwable) {
              -1L
            }
          }
        } finally {
          cursor.close()
        }
      }
      -1L
    } else {
      -1L
    }
  }

  fun subBuffer(buf: ByteBuffer, start: Int, count: Int = buf.remaining() - start): ByteBuffer {
    val newBuf = buf.duplicate()
    val bytes = ByteArray(count)
    newBuf.position(start)
    newBuf[bytes, 0, bytes.size]
    return ByteBuffer.wrap(bytes)
  }
}
