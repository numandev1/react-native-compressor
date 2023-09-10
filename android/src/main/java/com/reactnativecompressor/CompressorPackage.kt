package com.reactnativecompressor

import com.facebook.react.TurboReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.model.ReactModuleInfo
import com.facebook.react.module.model.ReactModuleInfoProvider

class CompressorPackage : TurboReactPackage() {
    override fun getModule(name: String, reactContext: ReactApplicationContext): NativeModule? {
        return if (name == CompressorModule.NAME) {
            CompressorModule(reactContext)
        } else {
            null
        }
    }

    override fun getReactModuleInfoProvider(): ReactModuleInfoProvider {
        return ReactModuleInfoProvider {
            val moduleInfos: MutableMap<String, ReactModuleInfo> = HashMap()
            val isTurboModule = BuildConfig.IS_NEW_ARCHITECTURE_ENABLED
            moduleInfos[CompressorModule.NAME] = ReactModuleInfo(
                    CompressorModule.NAME,
                    CompressorModule.NAME,
                    false,  // canOverrideExistingModule
                    false,  // needsEagerInit
                    true,  // hasConstants
                    false,  // isCxxModule
                    isTurboModule // isTurboModule
            )
            moduleInfos
        }
    }
}
