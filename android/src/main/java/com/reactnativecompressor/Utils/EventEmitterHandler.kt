package com.reactnativecompressor.Utils

import java.util.concurrent.ConcurrentHashMap

/**
 * Routes native progress emissions to the per-call JS callbacks that
 * `HybridCompressor` registers. This replaces the old `RCTDeviceEventEmitter`
 * bridge: Nitro delivers progress through callback parameters, so the domain
 * layer's `emit*` calls (unchanged) are dispatched to the callback registered
 * under the same `uuid` that the JS layer threads through the options map.
 */
class EventEmitterHandler {
  companion object {
    private val videoCompressProgressCallbacks = ConcurrentHashMap<String, (Double) -> Unit>()
    private val downloadProgressCallbacks = ConcurrentHashMap<String, (Double) -> Unit>()
    private val uploadProgressCallbacks = ConcurrentHashMap<String, (Double, Double) -> Unit>()

    @Volatile
    private var backgroundTaskExpiredCallback: (() -> Unit)? = null

    // Registration (called by HybridCompressor)

    fun registerVideoCompressProgress(uuid: String, callback: ((Double) -> Unit)?) {
      if (callback != null) videoCompressProgressCallbacks[uuid] = callback
    }

    fun registerDownloadProgress(uuid: String, callback: ((Double) -> Unit)?) {
      if (callback != null) downloadProgressCallbacks[uuid] = callback
    }

    fun registerUploadProgress(uuid: String, callback: ((Double, Double) -> Unit)?) {
      if (callback != null) uploadProgressCallbacks[uuid] = callback
    }

    fun unregister(uuid: String) {
      videoCompressProgressCallbacks.remove(uuid)
      downloadProgressCallbacks.remove(uuid)
      uploadProgressCallbacks.remove(uuid)
    }

    fun setBackgroundTaskExpiredCallback(callback: (() -> Unit)?) {
      backgroundTaskExpiredCallback = callback
    }

    // Emission (called by the domain layer — method names preserved)

    fun emitBackgroundTaskExpired(backgroundId: String?) {
      backgroundTaskExpiredCallback?.invoke()
    }

    fun emitVideoCompressProgress(progress: Double, uuid: String) {
      videoCompressProgressCallbacks[uuid]?.invoke(progress)
    }

    fun emitDownloadProgress(progress: Double, uuid: String) {
      downloadProgressCallbacks[uuid]?.invoke(progress)
    }

    fun emitDownloadProgressError(uuid: String?, error: String?) {
      // No JS consumer for `downloadProgressError`; download failures surface
      // through the rejected Promise instead. Kept so domain call sites compile.
    }

    fun sendUploadProgressEvent(numBytes: Long, totalBytes: Long, uuid: String?) {
      if (uuid != null) {
        uploadProgressCallbacks[uuid]?.invoke(numBytes.toDouble(), totalBytes.toDouble())
      }
    }
  }
}
