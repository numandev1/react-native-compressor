package com.reactnativecompressor.Video.VideoCompressor.video

import android.graphics.SurfaceTexture
import android.graphics.SurfaceTexture.OnFrameAvailableListener
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface

class OutputSurface : OnFrameAvailableListener {

    private var mSurfaceTexture: SurfaceTexture? = null
    private var mSurface: Surface? = null
    private val mFrameSyncObject = Object()
    private var mFrameAvailable = false
    private var mTextureRender: TextureRenderer? = null

    // Dedicated thread for SurfaceTexture's onFrameAvailable callback.
    // Without this, Android delivers the callback on the main UI thread
    // (because the compression coroutine has no Looper), so awaitNewImage()
    // stalls whenever the main thread is busy with UI / JS bridge work.
    // Routing the callback to its own thread removes that contention and
    // is the single biggest throughput win for the decoder→encoder pipeline.
    private val mCallbackThread = HandlerThread("CompressorSurfaceTexCb").apply { start() }
    private val mCallbackHandler = Handler(mCallbackThread.looper)

    /**
     * Creates an OutputSurface using the current EGL context. This Surface will be
     * passed to MediaCodec.configure().
     */
    init {
        setup()
    }

    /**
     * Creates instances of TextureRender and SurfaceTexture, and a Surface associated
     * with the SurfaceTexture.
     */
    private fun setup() {
        mTextureRender = TextureRenderer()
        mTextureRender?.let {
            it.surfaceCreated()

            // Even if we don't access the SurfaceTexture after the constructor returns, we
            // still need to keep a reference to it. The Surface doesn't retain a reference
            // at the Java level, so if we don't either then the object can get GCed, which
            // causes the native finalizer to run.
            mSurfaceTexture = SurfaceTexture(it.getTextureId())
            mSurfaceTexture?.let { surfaceTexture ->
                surfaceTexture.setOnFrameAvailableListener(this, mCallbackHandler)
                mSurface = Surface(mSurfaceTexture)
            }
        }
    }

    /**
     * Discards all resources held by this class, notably the EGL context.
     *
     * quitSafely() returns immediately; the HandlerThread's native pthread
     * may still be terminating when callers proceed to tear down MediaCodec.
     * If the ART sampling profiler walks threads during that window it can
     * dereference a stale pthread_t and SIGABRT. join(500) blocks until the
     * thread is fully exited (pthread_join) so the pthread_t is no longer
     * tracked. Bounded at 500ms to avoid hanging on a pathological looper.
     */
    fun release() {
        mSurface?.release()

        mTextureRender = null
        mSurface = null
        mSurfaceTexture = null

        mCallbackThread.quitSafely()
        try {
            mCallbackThread.join(500)
        } catch (ignored: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    /**
     * Returns the Surface that we draw onto.
     */
    fun getSurface(): Surface? = mSurface

    /**
     * Latches the next buffer into the texture.  Must be called from the thread that created
     * the OutputSurface object, after the onFrameAvailable callback has signaled that new
     * data is available.
     */
    fun awaitNewImage() {
        // 10s timeout to avoid spurious failures under heavy main-thread load.
        // The callback now arrives on a dedicated thread, so realistic frames
        // land in <50ms; this bound only catches a stuck pipeline.
        val timeOutMS = 10_000
        synchronized(mFrameSyncObject) {
            while (!mFrameAvailable) {
                try {
                    mFrameSyncObject.wait(timeOutMS.toLong())
                    if (!mFrameAvailable) {
                        throw RuntimeException("Surface frame wait timed out")
                    }
                } catch (ie: InterruptedException) {
                    throw RuntimeException(ie)
                }
            }
            mFrameAvailable = false
        }
        mTextureRender?.checkGlError("before updateTexImage")
        mSurfaceTexture?.updateTexImage()
    }

    /**
     * Draws the data from SurfaceTexture onto the current EGL surface.
     */
    fun drawImage() {
        mTextureRender?.drawFrame(mSurfaceTexture!!)
    }

    override fun onFrameAvailable(p0: SurfaceTexture?) {
        synchronized(mFrameSyncObject) {
            if (mFrameAvailable) {
                throw RuntimeException("mFrameAvailable already set, frame could be dropped")
            }
            mFrameAvailable = true
            mFrameSyncObject.notifyAll()
        }
    }
}
