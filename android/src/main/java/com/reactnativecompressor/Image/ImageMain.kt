package com.reactnativecompressor.Image

import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableMap
import com.reactnativecompressor.Utils.MediaCache
import com.reactnativecompressor.Utils.Utils

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
}
