package com.reactnativecompressor;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReadableMap;

public abstract class VideoCompressorSpec extends ReactContextBaseJavaModule {
  public VideoCompressorSpec(ReactApplicationContext context) {
    super(context);
  }

  public abstract void compress(String fileUrl, ReadableMap optionMap, Promise promise);

  public abstract void cancelCompression(String uuid);

  public abstract void upload(String fileUrl, ReadableMap options, Promise promise);

  public abstract void activateBackgroundTask(ReadableMap options, Promise promise);

  public abstract void deactivateBackgroundTask(ReadableMap options, Promise promise);

  public abstract void addListener(String eventName);

  public abstract void removeListeners(double count);
}
