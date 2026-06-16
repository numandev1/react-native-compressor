package com.margelo.nitro.compressor

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableMap
import com.margelo.nitro.NitroModules
import com.margelo.nitro.core.AnyMap
import com.margelo.nitro.core.Promise
import com.reactnativecompressor.Audio.AudioMain
import com.reactnativecompressor.Image.ImageMain
import com.reactnativecompressor.NitroPromiseAdapter
import com.reactnativecompressor.Utils.CreateVideoThumbnailClass
import com.reactnativecompressor.Utils.Downloader
import com.reactnativecompressor.Utils.EventEmitterHandler
import com.reactnativecompressor.Utils.Uploader
import com.reactnativecompressor.Utils.Utils
import com.reactnativecompressor.Video.VideoMain
import java.util.UUID
import java.util.concurrent.Executors
import androidx.annotation.Keep
import com.facebook.proguard.annotations.DoNotStrip
import com.facebook.react.bridge.Promise as RNPromise

/**
 * Nitro HybridObject implementation of the single `Compressor` native module.
 *
 * Thin binding layer: it converts Nitro's `AnyMap` options into the
 * `ReadableMap` the existing domain code already consumes, bridges the Nitro
 * `Promise` to the domain layer's `com.facebook.react.bridge.Promise` via
 * [NitroPromiseAdapter], and registers progress callbacks (keyed by `uuid`)
 * with [EventEmitterHandler]. All heavy logic stays in the domain classes,
 * which run on a background executor so the JS thread is never blocked.
 */
@DoNotStrip
@Keep
class HybridCompressor : HybridCompressorSpec() {
  private val reactContext
    get() = NitroModules.applicationContext
      ?: throw IllegalStateException("react-native-compressor: ReactApplicationContext is not available")

  private val executor = Executors.newCachedThreadPool()

  private val imageMain by lazy { ImageMain(reactContext) }
  private val videoMain by lazy { VideoMain(reactContext) }
  private val audioMain by lazy { AudioMain(reactContext) }
  private val uploader by lazy { Uploader(reactContext) }
  private val videoThumbnail by lazy { CreateVideoThumbnailClass(reactContext) }

  // region Converters

  private val toStringResult: (Any?) -> String = { it as? String ?: "" }

  private val toAnyMapResult: (Any?) -> AnyMap = { value ->
    val hashMap = (value as? ReadableMap)?.toHashMap() ?: HashMap()
    AnyMap.fromMap(hashMap, true)
  }

  private val toThumbnailResult: (Any?) -> VideoThumbnailResult = { value ->
    val map = value as ReadableMap
    VideoThumbnailResult(
      if (map.hasKey("path")) map.getString("path") ?: "" else "",
      if (map.hasKey("size")) map.getDouble("size") else 0.0,
      if (map.hasKey("mime")) map.getString("mime") ?: "" else "",
      if (map.hasKey("width")) map.getDouble("width") else 0.0,
      if (map.hasKey("height")) map.getDouble("height") else 0.0,
    )
  }

  // endregion

  // region Image

  override fun image_compress(imagePath: String, optionMap: AnyMap, onDownloadProgress: ((progress: Double) -> Unit)?): Promise<String> {
    val map = toWritableMap(optionMap)
    // Remote-image download progress is keyed by `uuid` inside the Downloader. The JS
    // layer no longer sends one (the callback is passed directly), so mint one here.
    val uuid = if (map.hasKey("uuid")) map.getString("uuid") ?: UUID.randomUUID().toString() else UUID.randomUUID().toString()
    map.putString("uuid", uuid)
    EventEmitterHandler.registerDownloadProgress(uuid, onDownloadProgress)
    return runOnExecutor(toStringResult, { EventEmitterHandler.unregister(uuid) }) { promise ->
      imageMain.image_compress(imagePath, map, promise)
    }
  }

  override fun getImageMetaData(filePath: String): Promise<AnyMap> {
    return runOnExecutor(toAnyMapResult) { promise -> imageMain.getImageMetaData(filePath, promise) }
  }

  // endregion

  // region Video

  override fun compress(
    fileUrl: String,
    optionMap: AnyMap,
    onProgress: ((progress: Double) -> Unit)?,
    onDownloadProgress: ((progress: Double) -> Unit)?,
  ): Promise<String> {
    val map = toWritableMap(optionMap)
    val uuid = if (map.hasKey("uuid")) map.getString("uuid") ?: "" else ""
    EventEmitterHandler.registerVideoCompressProgress(uuid, onProgress)
    EventEmitterHandler.registerDownloadProgress(uuid, onDownloadProgress)
    return runOnExecutor(toStringResult, { EventEmitterHandler.unregister(uuid) }) { promise ->
      videoMain.compress(fileUrl, map, promise)
    }
  }

  override fun cancelCompression(uuid: String) {
    videoMain.cancelCompression(uuid)
  }

  override fun getVideoMetaData(filePath: String): Promise<AnyMap> {
    return runOnExecutor(toAnyMapResult) { promise -> videoMain.getVideoMetaData(filePath, promise) }
  }

  override fun activateBackgroundTask(options: AnyMap, onExpired: (() -> Unit)?): Promise<String> {
    EventEmitterHandler.setBackgroundTaskExpiredCallback(onExpired)
    val map = toWritableMap(options)
    return runOnExecutor(toStringResult) { promise -> videoMain.activateBackgroundTask(map, promise) }
  }

  override fun deactivateBackgroundTask(options: AnyMap): Promise<String> {
    EventEmitterHandler.setBackgroundTaskExpiredCallback(null)
    val map = toWritableMap(options)
    return runOnExecutor(toStringResult) { promise -> videoMain.deactivateBackgroundTask(map, promise) }
  }

  // endregion

  // region Audio

  override fun compress_audio(fileUrl: String, optionMap: AnyMap): Promise<String> {
    val map = toWritableMap(optionMap)
    return runOnExecutor(toStringResult) { promise -> audioMain.compress_audio(fileUrl, map, promise) }
  }

  // endregion

  // region Upload / Download

  override fun upload(fileUrl: String, options: AnyMap, onProgress: ((written: Double, total: Double) -> Unit)?): Promise<AnyMap> {
    val map = toWritableMap(options)
    val uuid = if (map.hasKey("uuid")) map.getString("uuid") ?: "" else ""
    EventEmitterHandler.registerUploadProgress(uuid, onProgress)
    return runOnExecutor(toAnyMapResult, { EventEmitterHandler.unregister(uuid) }) { promise ->
      uploader.upload(fileUrl, map, reactContext, promise)
    }
  }

  override fun cancelUpload(uuid: String, shouldCancelAll: Boolean) {
    uploader.cancelUpload(uuid, shouldCancelAll)
  }

  override fun download(fileUrl: String, options: AnyMap, onProgress: ((progress: Double) -> Unit)?): Promise<String> {
    val uuid = if (options.contains("uuid") && options.isString("uuid")) options.getString("uuid") else ""
    val progressDivider = if (options.contains("progressDivider") && options.isDouble("progressDivider")) options.getDouble("progressDivider").toInt() else 0
    EventEmitterHandler.registerDownloadProgress(uuid, onProgress)
    return runOnExecutor(toStringResult, { EventEmitterHandler.unregister(uuid) }) { promise ->
      val downloadedFilePath = Downloader.downloadMediaWithProgress(fileUrl, uuid, progressDivider, reactContext)
      if (downloadedFilePath != null) promise.resolve(downloadedFilePath) else promise.reject("Unable to download", "Unable to download")
    }
  }

  // endregion

  // region Others

  override fun generateFilePath(fileExtension: String): Promise<String> {
    return runOnExecutor(toStringResult) { promise -> promise.resolve(Utils.generateCacheFilePath(fileExtension, reactContext)) }
  }

  override fun getRealPath(path: String, type: String): Promise<String> {
    // Utils.getRealPath already returns a `file://`-prefixed path via slashifyFilePath,
    // so it must not be prefixed again (that produced a malformed `file://file:///…`).
    return runOnExecutor(toStringResult) { promise -> promise.resolve(Utils.getRealPath(path, reactContext)) }
  }

  override fun getFileSize(filePath: String): Promise<String> {
    return runOnExecutor(toStringResult) { promise -> Utils.getFileSize(filePath, promise, reactContext) }
  }

  override fun createVideoThumbnail(fileUrl: String, options: AnyMap): Promise<VideoThumbnailResult> {
    val map = toWritableMap(options)
    return runOnExecutor(toThumbnailResult) { promise -> videoThumbnail.create(fileUrl, map, promise) }
  }

  override fun clearCache(cacheDir: String?): Promise<String> {
    return runOnExecutor(toStringResult) { promise -> CreateVideoThumbnailClass.clearCache(cacheDir, promise, reactContext) }
  }

  // endregion

  // region Helpers

  /** Convert a Nitro [AnyMap] to a React Native [WritableMap] that the domain parsers consume. */
  private fun toWritableMap(map: AnyMap): WritableMap = Arguments.makeNativeMap(map.toHashMap())

  /**
   * Runs [block] on a background thread, handing it a bridge [RNPromise] that
   * resolves/rejects the returned Nitro [Promise]. Synchronous throws are routed
   * to the same adapter so the Promise is never double-settled.
   */
  private fun <T> runOnExecutor(convert: (Any?) -> T, onSettle: () -> Unit = {}, block: (RNPromise) -> Unit): Promise<T> {
    val promise = Promise<T>()
    val adapter = NitroPromiseAdapter(promise, convert, onSettle)
    executor.execute {
      try {
        block(adapter)
      } catch (e: Throwable) {
        adapter.reject(e)
      }
    }
    return promise
  }

  // endregion
}
