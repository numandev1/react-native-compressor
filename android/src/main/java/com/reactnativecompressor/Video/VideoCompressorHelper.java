package com.reactnativecompressor.Video;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.PowerManager;

import androidx.annotation.Nullable;

import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableMapKeySetIterator;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.reactnativecompressor.Utils.FileUplaoder.FileUploader;

import java.util.UUID;

public class VideoCompressorHelper {
  private static ReactApplicationContext _reactContext;
  private static String backgroundId;
  private static Handler handler;
  private static Runnable runnable;
  private static PowerManager powerManager;
  private static PowerManager.WakeLock wakeLock;
  private final static LifecycleEventListener listener = new LifecycleEventListener(){
    @Override
    public void onHostResume() {}

    @Override
    public void onHostPause() {}

    @Override
    public void onHostDestroy() {
      if (wakeLock.isHeld()) {
        wakeLock.release();
        sendEventString(_reactContext, "backgroundTaskExpired", backgroundId);
      }
    }
  };

  private static void  sendEventString(ReactContext reactContext,
                               String eventName,
                               @Nullable String params) {
    reactContext
      .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
      .emit(eventName, params);
  }

  public static void video_upload_helper(String fileUrl, ReadableMap options, ReactApplicationContext reactContext, Promise promise) {
    FileUploader.upload(fileUrl,options,reactContext,promise);
  }

  @SuppressLint("InvalidWakeLockTag")
  public static String video_activateBackgroundTask_helper(ReadableMap options, ReactApplicationContext reactContext) {
    _reactContext=reactContext;
    backgroundId= UUID.randomUUID().toString();
    powerManager = (PowerManager) reactContext.getSystemService(reactContext.POWER_SERVICE);
    wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "bg_wakelock");
    reactContext.addLifecycleEventListener(listener);
    if (!wakeLock.isHeld()) {
      wakeLock.acquire();
    }

    handler = new Handler();
    runnable = new Runnable() {
      @Override
      public void run() {

      }
    };

    handler.post(runnable);
    return  "";
  }

  public static String video_deactivateBackgroundTask_helper(ReadableMap options,ReactApplicationContext reactContext) {

    if (wakeLock.isHeld()) wakeLock.release();

    // avoid null pointer exceptio when stop is called without start
    if (handler != null) handler.removeCallbacks(runnable);
    backgroundId="";

    return  "";
  }

  public static VideoCompressorHelper fromMap(ReadableMap map) {
    final VideoCompressorHelper options = new VideoCompressorHelper();
    final ReadableMapKeySetIterator iterator = map.keySetIterator();

    while (iterator.hasNextKey()) {
      final String key = iterator.nextKey();

      switch (key) {
        case "bitrate":
          options.bitrate = (float) map.getDouble(key);
          break;

          case "uuid":
          options.uuid = map.getString(key);
          break;

      }
    }
    return options;
  }
  public float bitrate = 0;
  public String uuid = "";
}
