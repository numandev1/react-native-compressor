package com.reactnativecompressor.Video;

import android.media.MediaMetadataRetriever;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.module.annotations.ReactModule;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.zolad.videoslimmer.VideoSlimmer;

import static com.reactnativecompressor.Utils.Utils.generateCacheFilePath;
import static com.reactnativecompressor.Video.VideoCompressorHelper.video_activateBackgroundTask_helper;
import static com.reactnativecompressor.Video.VideoCompressorHelper.video_deactivateBackgroundTask_helper;
import static com.reactnativecompressor.Video.VideoCompressorHelper.video_upload_helper;

@ReactModule(name = VideoModule.NAME)
public class VideoModule extends ReactContextBaseJavaModule {
  public static final String NAME = "VideoCompressor";
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
    try{
      final VideoCompressorHelper options = VideoCompressorHelper.fromMap(optionMap);
    String srcPath = fileUrl;
    if (srcPath.indexOf("file://") > -1) {
      srcPath = srcPath.substring(srcPath.indexOf(':') + 1);
    }
    String destinationPath = generateCacheFilePath("mp4", reactContext);

      MediaMetadataRetriever metaRetriever = new MediaMetadataRetriever();
      metaRetriever.setDataSource(srcPath);
      int height =Integer.parseInt(metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
      int width = Integer.parseInt(metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));

      boolean isPortrait = height > width;
      float maxSize = 1920;
      if(isPortrait && height > maxSize){
        width = (1920/height)*width;
        height = 1920;
      }else if(width > maxSize){
        height = (1920/width)*height;
        width = 1920;
      }

      float videoBitRate = (float) (height * width * 1.5);

    VideoSlimmer.convertVideo(srcPath, destinationPath, width, height, (int) videoBitRate, new VideoSlimmer.ProgressListener() {


      @Override
      public void onStart() {
        //convert start

      }

      @Override
      public void onFinish(boolean result) {
        //convert finish,result(true is success,false is fail)
        promise.resolve(destinationPath);
      }


      @Override
      public void onProgress(float percent) {
        WritableMap params = Arguments.createMap();
        WritableMap data = Arguments.createMap();
        params.putString("uuid", options.uuid);
        data.putDouble("progress",  percent/100);
        params.putMap("data", data);
        sendEvent(reactContext, "videoCompressProgress", params);
      }
    });

    } catch (Exception ex) {
      promise.reject(ex);
    }
  }

  @ReactMethod
  public void upload(
    String fileUrl,
    ReadableMap options,
    Promise promise) {
    try {
      String response=video_upload_helper(fileUrl,options,reactContext);
      promise.resolve(response);
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
