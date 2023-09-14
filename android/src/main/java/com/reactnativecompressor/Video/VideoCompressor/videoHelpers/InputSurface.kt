package com.reactnativecompressor.Video.VideoCompressor.video

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.view.Surface

class InputSurface(surface: Surface?) {

    private val eglRecordableAndroid = 0x3142
    private val eglOpenGlES2Bit = 4
    private var mEGLDisplay: EGLDisplay? = null
    private var mEGLContext: EGLContext? = null
    private var mEGLSurface: EGLSurface? = null
    private var mSurface: Surface? = null

    init {
        if (surface == null) {
            throw NullPointerException()
        }
        mSurface = surface
        eglSetup()
    }

    private fun eglSetup() {
        mEGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (mEGLDisplay === EGL14.EGL_NO_DISPLAY) {
            throw RuntimeException("unable to get EGL14 display")
        }
        val version = IntArray(2)
        if (!EGL14.eglInitialize(mEGLDisplay, version, 0, version, 1)) {
            mEGLDisplay = null
            throw RuntimeException("unable to initialize EGL14")
        }
        val attribList = intArrayOf(
            EGL14.EGL_RED_SIZE,
            8,
            EGL14.EGL_GREEN_SIZE,
            8,
            EGL14.EGL_BLUE_SIZE,
            8,
            EGL14.EGL_RENDERABLE_TYPE,
            eglOpenGlES2Bit,
            eglRecordableAndroid,
            1,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(
                mEGLDisplay, attribList, 0, configs, 0, configs.size,
                numConfigs, 0
            )
        ) {
            throw RuntimeException("unable to find RGB888+recordable ES2 EGL config")
        }
        val attrs = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL14.EGL_NONE
        )
        mEGLContext =
            EGL14.eglCreateContext(mEGLDisplay, configs[0], EGL14.EGL_NO_CONTEXT, attrs, 0)
        checkEglError()
        if (mEGLContext == null) {
            throw RuntimeException("null context")
        }
        val surfaceAttrs = intArrayOf(
            EGL14.EGL_NONE
        )
        mEGLSurface = EGL14.eglCreateWindowSurface(
            mEGLDisplay, configs[0], mSurface,
            surfaceAttrs, 0
        )
        checkEglError()
        if (mEGLSurface == null) {
            throw RuntimeException("surface was null")
        }
    }

    fun release() {
        if (EGL14.eglGetCurrentContext() == mEGLContext) {
            EGL14.eglMakeCurrent(
                mEGLDisplay,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT
            )
        }
        EGL14.eglDestroySurface(mEGLDisplay, mEGLSurface)
        EGL14.eglDestroyContext(mEGLDisplay, mEGLContext)

        mSurface?.release()

        mEGLDisplay = null
        mEGLContext = null
        mEGLSurface = null

        mSurface = null
    }

    fun makeCurrent() {
        if (!EGL14.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext)) {
            throw RuntimeException("eglMakeCurrent failed")
        }
    }

    fun swapBuffers(): Boolean =
        EGL14.eglSwapBuffers(mEGLDisplay, mEGLSurface)


    fun setPresentationTime(nsecs: Long) {
        EGLExt.eglPresentationTimeANDROID(mEGLDisplay, mEGLSurface, nsecs)
    }

    private fun checkEglError() {
        var failed = false
        while (EGL14.eglGetError() != EGL14.EGL_SUCCESS) {
            failed = true
        }
        if (failed) {
            throw RuntimeException("EGL error encountered (see log)")
        }
    }

}
