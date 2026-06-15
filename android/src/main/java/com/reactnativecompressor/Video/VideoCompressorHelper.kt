package com.reactnativecompressor.Video

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import com.facebook.react.bridge.LifecycleEventListener
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableMap
import com.reactnativecompressor.Utils.EventEmitterHandler
import com.reactnativecompressor.Utils.Utils
import java.util.UUID

class VideoCompressorHelper {
    enum class CompressionMethod {
        auto,
        manual
    }

    var compressionMethod = CompressionMethod.auto
    var bitrate = 0f
    var uuid: String? = ""
    var maxSize = 640.0f
    var progressDivider: Int? = 0
    var minimumFileSizeForCompress = 0.0f
    var stripAudio = false

    companion object {
        private var _reactContext: ReactApplicationContext? = null
        private var backgroundId: String? = null
        private var handler: Handler? = null
        private var runnable: Runnable? = null
        private var powerManager: PowerManager? = null
        private var wakeLock: WakeLock? = null
        private val listener: LifecycleEventListener = object : LifecycleEventListener {
            override fun onHostResume() {}
            override fun onHostPause() {}
            override fun onHostDestroy() {
                if (wakeLock!!.isHeld) {
                    wakeLock!!.release()
                  EventEmitterHandler.emitBackgroundTaskExpired(backgroundId)

                }
            }
        }

        @SuppressLint("InvalidWakeLockTag")
        fun video_activateBackgroundTask_helper(options: ReadableMap?, reactContext: ReactApplicationContext): String {
            _reactContext = reactContext
            backgroundId = UUID.randomUUID().toString()
            powerManager = reactContext.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager!!.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "bg_wakelock")
            reactContext.addLifecycleEventListener(listener)
            if (!wakeLock!!.isHeld()) {
                wakeLock!!.acquire()
            }
            // Bind to the main Looper: under Nitro this runs on a background executor
            // thread (no Looper), so the no-arg Handler() constructor would throw.
            handler = Handler(Looper.getMainLooper())
            runnable = Runnable { }
            handler!!.post(runnable!!)
            return ""
        }

        fun video_deactivateBackgroundTask_helper(options: ReadableMap?, reactContext: ReactApplicationContext?): String {
            if (wakeLock!!.isHeld) wakeLock!!.release()

            // avoid null pointer exceptio when stop is called without start
            if (handler != null) handler!!.removeCallbacks(runnable!!)
            backgroundId = ""
            return ""
        }

        fun fromMap(map: ReadableMap): VideoCompressorHelper {
            val options = VideoCompressorHelper()
            val iterator = map.keySetIterator()
            while (iterator.hasNextKey()) {
                val key = iterator.nextKey()
                when (key) {
                    "compressionMethod" -> options.compressionMethod = CompressionMethod.valueOf(map.getString(key)!!)
                    "maxSize" -> options.maxSize = map.getDouble(key).toFloat()
                    "uuid" -> options.uuid = map.getString(key)
                    "minimumFileSizeForCompress" -> options.minimumFileSizeForCompress = map.getDouble(key).toFloat()
                    "bitrate" -> options.bitrate = map.getDouble(key).toFloat()
                    "progressDivider" -> options.progressDivider = map.getInt(key)
                    "stripAudio" -> options.stripAudio = map.getBoolean(key)
                }
            }
            return options
        }

        fun getMetadataInt(metaRetriever: MediaMetadataRetriever, key: Int): Int {
            return metaRetriever.extractMetadata(key)
                ?.toDoubleOrNull()
                ?.toLong()
                ?.coerceIn(0L, Int.MAX_VALUE.toLong())
                ?.toInt()
                ?: 0
        }

        /**
         * Derive the source video frame rate. METADATA_KEY_CAPTURE_FRAMERATE
         * is only populated for slow-motion captures, so most regular videos
         * return 0 and downstream code falls back to a hard-coded 30 fps —
         * which silently halves the frame count of any 60 fps source and
         * produces visibly choppy output.
         *
         * Strategy: trust CAPTURE_FRAMERATE when present, otherwise compute
         * fps from frame count / duration (API 28+ exposes the frame count
         * via METADATA_KEY_VIDEO_FRAME_COUNT). Returns 0 if neither path
         * yields a usable value.
         */
        fun getSourceFrameRate(metaRetriever: MediaMetadataRetriever): Int {
            val capture = getMetadataInt(metaRetriever, MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
            if (capture > 0) return capture

            val frameCount = getMetadataInt(metaRetriever, MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)
            val durationMs = getMetadataInt(metaRetriever, MediaMetadataRetriever.METADATA_KEY_DURATION)
            if (frameCount <= 0 || durationMs <= 0) return 0
            val fps = (frameCount.toLong() * 1000L / durationMs.toLong()).toInt()
            return fps.coerceIn(0, 240)
        }

        fun VideoCompressManual(fileUrl: String?, options: VideoCompressorHelper, promise: Promise, reactContext: ReactApplicationContext?) {
            try {
                val uri = Uri.parse(fileUrl)
                val srcPath = uri.path
                val destinationPath = Utils.generateCacheFilePath("mp4", reactContext!!)
                val metaRetriever = MediaMetadataRetriever()
                metaRetriever.setDataSource(srcPath)
                val height = getMetadataInt(metaRetriever, MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                val width = getMetadataInt(metaRetriever, MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                val bitrate = getMetadataInt(metaRetriever, MediaMetadataRetriever.METADATA_KEY_BITRATE)
                val frameRate = getSourceFrameRate(metaRetriever)
                if (height <= 0 || width <= 0) {
                    promise.reject(Throwable("Failed to read the input video dimensions"))
                    return
                }
                val profile = VideoCompressionProfileFactory.createManual(
                    sourceWidth = width,
                    sourceHeight = height,
                    sourceBitrate = bitrate,
                    sourceFrameRate = frameRate,
                    maxSize = options.maxSize,
                    requestedBitrate = options.bitrate,
                )
                Utils.compressVideo(
                    srcPath!!,
                    destinationPath,
                    profile.width,
                    profile.height,
                    profile.bitrate.toFloat(),
                    profile.frameRate,
                    options.uuid!!,
                    options.progressDivider!!,
                    options.stripAudio,
                    promise,
                    reactContext,
                )
            } catch (ex: Exception) {
                promise.reject(ex)
            }
        }

        fun VideoCompressAuto(fileUrl: String?, options: VideoCompressorHelper?, promise: Promise?, reactContext: ReactApplicationContext?) {
            AutoVideoCompression.createCompressionSettings(fileUrl, options!!, promise!!, reactContext)
        }
    }
}
