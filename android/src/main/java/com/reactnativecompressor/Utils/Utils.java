package com.reactnativecompressor.Utils;

import static com.facebook.react.bridge.UiThreadUtil.runOnUiThread;

import androidx.annotation.Nullable;

import com.abedelazizshe.lightcompressorlibrary.CompressionListener;
import com.abedelazizshe.lightcompressorlibrary.config.Configuration;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import kotlin.Pair;
import numan.dev.videoslimmer.VideoSlimTask;
import numan.dev.videoslimmer.VideoSlimmer;
import com.abedelazizshe.lightcompressorlibrary.VideoCompressor;
import com.abedelazizshe.lightcompressorlibrary.VideoQuality;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Utils {
  static int videoCompressionThreshold=10;
  static int currentVideoCompression=0;
  static Map<String, VideoCompressor> compressorExports = new HashMap<>();

  public static String generateCacheFilePath(String extension, ReactApplicationContext reactContext){
    File outputDir = reactContext.getCacheDir();

    String outputUri = String.format("%s/%s." + extension, outputDir.getPath(), UUID.randomUUID().toString());
    return outputUri;
  }


  public static void compressVideo(String srcPath, String destinationPath, int resultWidth, int resultHeight, float videoBitRate, String uuid, Promise promise, ReactApplicationContext reactContext){
    VideoCompressor nomi=new VideoCompressor();
    nomi.start(
      null,
      null, // => Source can be provided as content uri, it requires context.
      srcPath, // => This could be null if srcUri and context are provided.
      destinationPath,
      null, /*String, or null*/
      new CompressionListener() {
        @Override
        public void onStart() {
          // Compression start
        }

        @Override
        public void onSuccess() {
          promise.resolve("file:/"+destinationPath);
        }

        @Override
        public void onFailure(String failureMessage) {
          // On Failure
        }

        @Override
        public void onProgress(float percent) {
          runOnUiThread(new Runnable() {
            public void run() {
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
        }

        @Override
        public void onCancelled() {
          promise.resolve(srcPath);
        }
      }, new Configuration(
        VideoQuality.VERY_HIGH,
        25,
        false,
        (int) videoBitRate,
        false,
        new Pair(resultWidth,resultHeight)
      )
    );
    currentVideoCompression=0;
  }

  public static void cancelCompressionHelper(String uuid){
    try{
//      VideoCompressor.cancel();
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
