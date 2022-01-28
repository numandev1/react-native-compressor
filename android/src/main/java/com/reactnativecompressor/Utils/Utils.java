package com.reactnativecompressor.Utils;

import android.net.Uri;
import android.util.Log;

import androidx.annotation.Nullable;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import numan.dev.videocompressor.VideoCompressTask;
import numan.dev.videocompressor.VideoCompressor;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Utils {
  static int videoCompressionThreshold=10;
  private static final String TAG = "react-native-compessor";
  static Map<String, VideoCompressTask> compressorExports = new HashMap<>();

  public static String generateCacheFilePath(String extension, ReactApplicationContext reactContext){
    File outputDir = reactContext.getCacheDir();

    String outputUri = String.format("%s/%s." + extension, outputDir.getPath(), UUID.randomUUID().toString());
    return outputUri;
  }


  public static void compressVideo(String srcPath, String destinationPath, int resultWidth, int resultHeight, float videoBitRate, String uuid, Promise promise, ReactApplicationContext reactContext){
    final int[] currentVideoCompression = {0};
    try{
      VideoCompressTask export=VideoCompressor.convertVideo(srcPath, destinationPath, resultWidth, resultHeight, (int) videoBitRate, new VideoCompressor.ProgressListener() {
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
        if(errorMessage.equals(("class java.lang.AssertionError")))
        {
          promise.resolve(srcPath);
        }
        else
        {
          promise.reject("Compression has canncelled");
        }
      }

      @Override
      public void onProgress(float percent) {
        int roundProgress=Math.round(percent);
        if(roundProgress%videoCompressionThreshold==0&&roundProgress> currentVideoCompression[0]) {
          WritableMap params = Arguments.createMap();
          WritableMap data = Arguments.createMap();
          params.putString("uuid", uuid);
          data.putDouble("progress", percent / 100);
          params.putMap("data", data);
          sendEvent(reactContext, "videoCompressProgress", params);
          currentVideoCompression[0] =roundProgress;
        }
      }
    });
      compressorExports.put(uuid, export);
  } catch (Exception ex) {
    promise.reject(ex);
  }
    finally {
    currentVideoCompression[0] =0;
  }
  }

  public static void cancelCompressionHelper(String uuid){
    try{
      VideoCompressTask export=compressorExports.get(uuid);
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

  public static String getRealPath(String fileUrl,ReactApplicationContext reactContext){
    if(fileUrl.startsWith("content://"))
    {
      try {
        Uri uri= Uri.parse(fileUrl);
        fileUrl= RealPathUtil.getRealPath(reactContext,uri);
      }
      catch (Exception ex) {
        Log.d(TAG, " Please see this issue: https://github.com/Shobbak/react-native-compressor/issues/25");
      }
    }
    return fileUrl;
  }
}
