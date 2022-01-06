package com.reactnativecompressor.Video;

import android.annotation.SuppressLint;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.os.PowerManager;

import androidx.annotation.Nullable;

import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableMapKeySetIterator;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.reactnativecompressor.Utils.FileUplaoder.FileUploader;
import com.reactnativecompressor.Video.AutoVideoCompression.AutoVideoCompression;

import java.util.UUID;

import static com.reactnativecompressor.Utils.Utils.compressVideo;
import static com.reactnativecompressor.Utils.Utils.generateCacheFilePath;

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
  public enum CompressionMethod {
    auto, manual
  }

  public VideoCompressorHelper.CompressionMethod compressionMethod = VideoCompressorHelper.CompressionMethod.manual;
  public float bitrate = 0;
  public String uuid = "";
  public float maxSize = 640.0f;
  public float minimumFileSizeForCompress = 16.0f;

  public static VideoCompressorHelper fromMap(ReadableMap map) {
    final VideoCompressorHelper options = new VideoCompressorHelper();
    final ReadableMapKeySetIterator iterator = map.keySetIterator();

    while (iterator.hasNextKey()) {
      final String key = iterator.nextKey();

      switch (key) {
        case "compressionMethod":
          options.compressionMethod = VideoCompressorHelper.CompressionMethod.valueOf(map.getString(key));
          break;
        case "maxSize":
          options.maxSize = (float) map.getDouble(key);
          break;
        case "uuid":
          options.uuid = map.getString(key);
          break;
        case "minimumFileSizeForCompress":
          options.minimumFileSizeForCompress =(float) map.getDouble(key);
          break;

      }
    }
    return options;
  }

  private static void sendEvent(ReactContext reactContext,
                                String eventName,
                                @Nullable WritableMap params) {
    reactContext
      .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
      .emit(eventName, params);
  }
  static int videoCompressionThreshold=10;
  static int currentVideoCompression=0;
  public static void VideoCompressManual(String fileUrl,VideoCompressorHelper options,Promise promise, ReactApplicationContext reactContext) {
    try{
      Uri uri= Uri.parse(fileUrl);
      String srcPath = uri.getPath();
      String destinationPath = generateCacheFilePath("mp4", reactContext);
      MediaMetadataRetriever metaRetriever = new MediaMetadataRetriever();
      metaRetriever.setDataSource(srcPath);
      int height =Integer.parseInt(metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
      int width = Integer.parseInt(metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
      int bitrate=Integer.parseInt(metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE));

      boolean isPortrait = height > width;
      int maxSize = 1920;
      if(isPortrait && height > maxSize){
        width = (int) (((float)maxSize/height)*width);
        height = maxSize;
      }else if(width > maxSize){
        height = (int) (((float)maxSize/width)*height);
        width = maxSize;
      }
      else
      {
        if(options.bitrate==0) {
          options.bitrate = (int) (bitrate * 0.8);
        }
      }
      float videoBitRate = (options.bitrate>0)?options.bitrate: (float) (height * width * 1.5);
      compressVideo(srcPath, destinationPath, width, height,  videoBitRate,options.uuid,promise,reactContext);
    } catch (Exception ex) {
      promise.reject(ex);
    }
  }


  public static void VideoCompressAuto(String fileUrl,VideoCompressorHelper options,Promise promise, ReactApplicationContext reactContext) {
    AutoVideoCompression.createCompressionSettings(fileUrl,options,promise,reactContext);
  }


}
