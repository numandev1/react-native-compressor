package com.reactnativecompressor.Image

import android.media.ExifInterface
import android.net.Uri
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableMap
import com.reactnativecompressor.Utils.MediaCache
import com.reactnativecompressor.Utils.Utils
import com.reactnativecompressor.Utils.Utils.exifAttributes
import java.io.File

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
      val params = Arguments.createMap()
      val file = File(srcPath)
      val sizeInBytes = file.length().toDouble()
      val exif = ExifInterface(srcPath!!)
      for (tag in exifAttributes) {
        val value: String? = exif.getAttribute(tag)
        if(value != null)
        {
          params.putString(tag, value)
        }

      }
      val extension = filePath!!.substring(filePath.lastIndexOf(".") + 1)
      params.putDouble("size", sizeInBytes)
      params.putString("extension", extension)
      promise.resolve(params)
    } catch (e: Exception) {
      promise.reject(e)
    }
  }
}
