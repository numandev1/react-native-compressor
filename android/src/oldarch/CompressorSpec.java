package com.reactnativecompressor;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReadableMap;

abstract class CompressorSpec extends ReactContextBaseJavaModule {
  CompressorSpec(ReactApplicationContext context) {
    super(context);
  }

  public abstract void image_compress(
      String imagePath,
      ReadableMap optionMap,
      Promise promise);

  public abstract void compress_audio(
      String fileUrl,
      ReadableMap optionMap,
      Promise promise);

  public abstract void generateFilePath(String extension, Promise promise);

  public abstract void getRealPath(String path, String type, Promise promise);

  public abstract void getVideoMetaData(String filePath, Promise promise);

  public abstract void getFileSize(String filePath, Promise promise);

  public abstract void addListener(String eventName);

  public abstract void removeListeners(double count);
}
