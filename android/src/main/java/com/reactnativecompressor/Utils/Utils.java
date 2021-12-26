package com.reactnativecompressor.Utils;

import com.facebook.react.bridge.ReactApplicationContext;

import java.io.File;
import java.util.UUID;

public class Utils {
  public static String generateCacheFilePath(String extension, ReactApplicationContext reactContext){
    File outputDir = reactContext.getCacheDir();

    String outputUri = String.format("%s/%s." + extension, outputDir.getPath(), UUID.randomUUID().toString());
    return outputUri;
  }


  public static void compressVideo(String srcPath, String destinationPath, int resultWidth, int resultHeight, float videoBitRate, String uuid, Promise promise, ReactApplicationContext reactContext){
    try{
    VideoSlimTask export=VideoSlimmer.convertVideo(srcPath, destinationPath, resultWidth, resultHeight, (int) videoBitRate, new VideoSlimmer.ProgressListener() {
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
      public void onError(String errorMessage) {
        promise.reject("Compression has canncelled");
      }

      @Override
      public void onProgress(float percent) {
        int roundProgress=Math.round(percent);
        if(roundProgress%videoCompressionThreshold==0&&roundProgress>currentVideoCompression) {
          WritableMap params = Arguments.createMap();
          WritableMap data = Arguments.createMap();
          params.putString("uuid", uuid);
          data.putDouble("progress", percent / 100);
          params.putMap("data", data);
          sendEvent(reactContext, "videoCompressProgress", params);
          currentVideoCompression=roundProgress;
        }
      }
    });
      compressorExports.put(uuid, export);
  } catch (Exception ex) {
    promise.reject(ex);
  }
    finally {
    currentVideoCompression=0;
  }
  }

  public static void cancelCompressionHelper(String uuid){
    try{
      VideoSlimTask export=compressorExports.get(uuid);
      export.cancel(true);
    }
    catch (Exception ex) {
    }
  }

  private static void sendEvent(ReactContext reactContext,
                                String eventName,
                                @Nullable WritableMap params) {
    reactContext
      .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
      .emit(eventName, params);
  }
}
