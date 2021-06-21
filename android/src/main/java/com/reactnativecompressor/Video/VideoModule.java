package com.reactnativecompressor.Video;

import android.media.MediaMetadataRetriever;
import android.net.Uri;

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
  int videoCompressionThreshold=10;
  int currentVideoCompression=0;
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

    VideoSlimmer.convertVideo(srcPath, destinationPath, width, height, (int) videoBitRate, new VideoSlimmer.ProgressListener() {


      @Override
      public void onStart() {
        //convert start

      }

      @Override
      public void onFinish(boolean result) {
        //convert finish,result(true is success,false is fail)
        promise.resolve("file:/"+destinationPath);
      }


      @Override
      public void onProgress(float percent) {
        int roundProgress=Math.round(percent);
        if(roundProgress%videoCompressionThreshold==0&&roundProgress>currentVideoCompression) {
          WritableMap params = Arguments.createMap();
          WritableMap data = Arguments.createMap();
          params.putString("uuid", options.uuid);
          data.putDouble("progress", percent / 100);
          params.putMap("data", data);
          sendEvent(reactContext, "videoCompressProgress", params);
          currentVideoCompression=roundProgress;
        }
      }
    });

    } catch (Exception ex) {
      promise.reject(ex);
    }
    finally {
      currentVideoCompression=0;
    }
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
