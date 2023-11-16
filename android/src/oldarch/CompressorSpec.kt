package com.reactnativecompressor

import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReadableMap

abstract class CompressorSpec(context: ReactApplicationContext?) : ReactContextBaseJavaModule(context) {
    abstract fun image_compress(
            imagePath: String,
            optionMap: ReadableMap,
            promise: Promise)

    abstract fun compress_audio(
            fileUrl: String,
            optionMap: ReadableMap,
            promise: Promise)

    abstract fun generateFilePath(_extension: String, promise: Promise)
    abstract fun getRealPath(path: String, type: String, promise: Promise)
    abstract fun getVideoMetaData(filePath: String, promise: Promise)
    abstract fun getImageMetaData(filePath: String, promise: Promise);
    abstract fun getFileSize(filePath: String, promise: Promise)
    abstract fun compress(fileUrl: String, optionMap: ReadableMap, promise: Promise)
    abstract fun cancelCompression(uuid: String)
    abstract fun upload(fileUrl: String, options: ReadableMap, promise: Promise)
    abstract fun cancelUpload(uuid: String, shouldCancelAll:Boolean)

    abstract fun download(fileUrl: String, options: ReadableMap, promise: Promise)
    abstract fun activateBackgroundTask(options: ReadableMap, promise: Promise)
    abstract fun deactivateBackgroundTask(options: ReadableMap, promise: Promise)
    abstract fun createVideoThumbnail(fileUrl: String, options: ReadableMap, promise: Promise)
    abstract fun clearCache(cacheDir: String?, promise: Promise)
    abstract fun addListener(eventName: String)
    abstract fun removeListeners(count: Double)
}
