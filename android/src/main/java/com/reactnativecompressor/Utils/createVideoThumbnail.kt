package com.reactnativecompressor.Utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.text.TextUtils
import android.webkit.URLUtil
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContext
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.io.UnsupportedEncodingException
import java.lang.ref.WeakReference
import java.net.URLDecoder
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CreateVideoThumbnailClass(private val reactContext: ReactApplicationContext) {
    @ReactMethod
    fun create(fileUrl: String, options: ReadableMap, promise: Promise) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val result = processDataInBackground(reactContext, fileUrl, options)
                promise.resolve(result)
            } catch (e: Exception) {
                promise.reject("CreateVideoThumbnail_ERROR", e)
            }
        }
    }

    private suspend fun processDataInBackground(reactContext: ReactContext, filePath: String, options: ReadableMap): ReadableMap? = withContext(Dispatchers.IO) {
        val weakContext = WeakReference(reactContext.applicationContext)
        val format = "jpeg"
        val cacheName = if (options.hasKey("cacheName")) options.getString("cacheName") else ""
        val thumbnailDir = weakContext.get()!!.applicationContext.cacheDir.absolutePath + "/thumbnails"
        val cacheDir = createDirIfNotExists(thumbnailDir)

        if (!TextUtils.isEmpty(cacheName)) {
            val file = File(thumbnailDir, "$cacheName.$format")
            if (file.exists()) {
                val map = Arguments.createMap()
                map.putString("path", "file://" + file.absolutePath)
                val image = BitmapFactory.decodeFile(file.absolutePath)
                map.putDouble("size", image.byteCount.toDouble())
                map.putString("mime", "image/$format")
                map.putDouble("width", image.width.toDouble())
                map.putDouble("height", image.height.toDouble())
                return@withContext map
            }
        }

        val headers: Map<String, String> = if (options.hasKey("headers")) options.getMap("headers")!!.toHashMap() as Map<String, String> else HashMap<String, String>()
        val quality = if (options.hasKey("quality")) (options.getDouble("quality") * 100).toInt().coerceIn(0, 100) else DEFAULT_THUMBNAIL_QUALITY
        val fileName = if (TextUtils.isEmpty(cacheName)) "thumb-" + UUID.randomUUID().toString() else "$cacheName.$format"
        var fOut: OutputStream? = null

        try {
            val file = File(cacheDir, fileName)
            val context = weakContext.get()
            val image = getBitmapAtTime(context, filePath, 0, headers)
            file.createNewFile()
            fOut = FileOutputStream(file)

            // 100 means no compression, the lower you go, the stronger the compression
            image.compress(Bitmap.CompressFormat.JPEG, quality, fOut)
            fOut.flush()
            fOut.close()

            val map = Arguments.createMap()
            map.putString("path", "file://" + file.absolutePath)
            map.putDouble("size", image.byteCount.toDouble())
            map.putString("mime", "image/$format")
            map.putDouble("width", image.width.toDouble())
            map.putDouble("height", image.height.toDouble())
            return@withContext map
        } catch (e: Exception) {
            throw e
        } finally {
            try {
                fOut?.close()
            } catch (e: IOException) {
                // Ignore
            }
        }
    }

    companion object {
        private const val DEFAULT_THUMBNAIL_QUALITY = 90

        // delete previously added files one by one untill requred space is available
        fun clearCache(cacheDir: String?,promise:Promise, reactContext: ReactApplicationContext) {
          val cacheDirectory=cacheDir?.takeIf { it.isNotEmpty() } ?:"/thumbnails"
          val thumbnailDir: String = reactContext.getApplicationContext().getCacheDir().getAbsolutePath() + cacheDirectory
          val thumbnailDirFile = createDirIfNotExists(thumbnailDir)

          if (thumbnailDirFile != null) {
            val files = thumbnailDirFile.listFiles()

            // Loop through the files and delete them
            if (files != null) {
              for (file in files) {
                if (file.isFile) {
                  file.delete()
                }
              }
            }
          }
          promise.resolve("done")
        }

        private fun createDirIfNotExists(path: String): File {
            val dir = File(path)
            if (dir.exists()) {
                return dir
            }
            try {
                dir.mkdirs()
                // Add .nomedia to hide the thumbnail directory from gallery
                val noMedia = File(path, ".nomedia")
                noMedia.createNewFile()
            } catch (e: IOException) {
                e.printStackTrace()
            }
            return dir
        }

        private fun getBitmapAtTime(context: Context?, filePath: String?, time: Int, headers: Map<String, String>): Bitmap {
            check(!filePath.isNullOrEmpty()) { "Video file path cannot be null or empty" }
            val retriever = MediaMetadataRetriever()
            try {
                if (URLUtil.isFileUrl(filePath)) {
                    val decodedPath: String? = try {
                        URLDecoder.decode(filePath, "UTF-8")
                    } catch (e: UnsupportedEncodingException) {
                        filePath
                    }
                    retriever.setDataSource(decodedPath!!.replace("file://", ""))
                } else if (filePath.contains("content://")) {
                    retriever.setDataSource(context, Uri.parse(filePath))
                } else {
                    check(Build.VERSION.SDK_INT >= 14) { "Remote videos aren't supported on sdk_version < 14" }
                    retriever.setDataSource(filePath, headers)
                }

                val requestedTimeUs = (time * 1000).toLong()
                val frameAttempts = arrayOf(
                    Pair(requestedTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC),
                    Pair(requestedTimeUs, MediaMetadataRetriever.OPTION_CLOSEST),
                    Pair(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC),
                )
                for ((timeUs, option) in frameAttempts) {
                    val image = try {
                        retriever.getFrameAtTime(timeUs, option)
                    } catch (e: RuntimeException) {
                        null
                    }
                    if (image != null) {
                        return image
                    }
                }
                error("Unable to extract video frame from file")
            } finally {
                try {
                    retriever.release()
                } catch (e: IOException) {
                    // Ignore
                }
            }
        }
    }
}
