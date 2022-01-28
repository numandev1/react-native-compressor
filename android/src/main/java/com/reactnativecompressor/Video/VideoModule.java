package com.reactnativecompressor.Video;

import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.module.annotations.ReactModule;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.reactnativecompressor.Utils.RealPathUtil;

import static com.reactnativecompressor.Utils.Utils.getRealPath;
import static com.reactnativecompressor.Video.VideoCompressorHelper.video_activateBackgroundTask_helper;
import static com.reactnativecompressor.Video.VideoCompressorHelper.video_deactivateBackgroundTask_helper;
import static com.reactnativecompressor.Video.VideoCompressorHelper.video_upload_helper;
import static com.reactnativecompressor.Utils.Utils.cancelCompressionHelper;

@ReactModule(name = VideoModule.NAME)
public class  VideoModule extends ReactContextBaseJavaModule {
  public static final String NAME = "VideoCompressor";
  private static final String TAG = "react-native-compessor";
  private final ReactApplicationContext reactContext;
  public VideoModule(ReactApplicationContext reactContext) {
    super(reactContext);
    this.reactContext = reactContext;
  }

  @NonNull
  @Override
  public String getName() {
    return NAME;
  }

  private void sendEvent(ReactContext reactContext,
                         String eventName,
                         @Nullable WritableMap params) {
    reactContext
      .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
      .emit(eventName, params);
  }

  //Video
  @ReactMethod
  public void compress(
    String fileUrl,
    ReadableMap optionMap,
    Promise promise) {
    final VideoCompressorHelper options = VideoCompressorHelper.fromMap(optionMap);
    fileUrl=getRealPath(fileUrl,reactContext);

    if(options.compressionMethod==VideoCompressorHelper.CompressionMethod.auto)
    {
      VideoCompressorHelper.VideoCompressAuto(fileUrl,options,promise,reactContext);
    }
    else
    {
      VideoCompressorHelper.VideoCompressManual(fileUrl,options,promise,reactContext);
    }


  }

  @ReactMethod
  public void cancelCompression(
    String uuid) {
    cancelCompressionHelper(uuid);
    Log.d("cancelCompression", uuid);
  }

  @ReactMethod
  public void upload(
    String fileUrl,
    ReadableMap options,
    Promise promise) {
    try {
      video_upload_helper(fileUrl,options,reactContext,promise);
    } catch (Exception ex) {
      promise.reject(ex);
    }
  }

  @ReactMethod
  public void activateBackgroundTask(
    ReadableMap options,
    Promise promise) {
    try {
      String response=video_activateBackgroundTask_helper(options,reactContext);
      promise.resolve(response);
    } catch (Exception ex) {
      promise.reject(ex);
    }
  }

  @ReactMethod
  public void deactivateBackgroundTask(
    ReadableMap options,
    Promise promise) {
    try {
      String response=video_deactivateBackgroundTask_helper(options,reactContext);
      promise.resolve(response);
    } catch (Exception ex) {
      promise.reject(ex);
    }
  }
}
