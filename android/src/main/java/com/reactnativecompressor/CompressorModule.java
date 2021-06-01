package com.reactnativecompressor;

import androidx.annotation.NonNull;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.module.annotations.ReactModule;

@ReactModule(name = CompressorModule.NAME)
public class CompressorModule extends ReactContextBaseJavaModule {
    public static final String NAME = "Compressor";

    public CompressorModule(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @Override
    @NonNull
    public String getName() {
        return NAME;
    }


    @ReactMethod
   public void generateFile(String extension, Promise promise) {
     try {
       File outputDir = reactContext.getCacheDir();

       final String outputUri = String.format("%s/%s." + extension, outputDir.getPath(), UUID.randomUUID().toString());

       promise.resolve(outputUri);
     } catch (Exception e) {
       promise.reject(e);
     }
   }
}
