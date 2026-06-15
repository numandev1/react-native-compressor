package com.reactnativecompressor

import com.facebook.react.BaseReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.model.ReactModuleInfoProvider
import com.margelo.nitro.compressor.NitroCompressorOnLoad

/**
 * Empty React package whose sole jobs are:
 * 1. Make this library discoverable by React Native autolinking (which keys off a
 *    `ReactPackage`), so the Gradle project + native `.so` get wired into the app.
 * 2. Load the Nitro C++ library (`libNitroCompressor.so`) on first class-load so the
 *    `Compressor` HybridObject is registered. The module itself is served by Nitro,
 *    not by `getModule`, so no native modules are returned here.
 *
 * Lives in the `com.reactnativecompressor` namespace (not `com.margelo.nitro.compressor`)
 * because the React Native CLI derives the autolink import path from the Android namespace.
 */
class NitroCompressorPackage : BaseReactPackage() {
  override fun getModule(name: String, reactContext: ReactApplicationContext): NativeModule? = null

  override fun getReactModuleInfoProvider(): ReactModuleInfoProvider = ReactModuleInfoProvider { HashMap() }

  companion object {
    init {
      NitroCompressorOnLoad.initializeNative()
    }
  }
}
