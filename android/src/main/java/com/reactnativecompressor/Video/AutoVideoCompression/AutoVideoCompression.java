package com.reactnativecompressor.Video.AutoVideoCompression;

import android.media.MediaMetadataRetriever;
import android.net.Uri;

import androidx.annotation.Nullable;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.reactnativecompressor.Video.VideoCompressorHelper;
import static com.reactnativecompressor.Utils.Utils.compressVideo;

import java.io.File;

import static com.reactnativecompressor.Utils.Utils.generateCacheFilePath;

public class AutoVideoCompression {
  public static void createCompressionSettings(String fileUrl,VideoCompressorHelper options,Promise promise, ReactApplicationContext reactContext) {
    float maxSize = options.maxSize;
    float minimumFileSizeForCompress=options.minimumFileSizeForCompress;
    try{
      Uri uri= Uri.parse(fileUrl);
      String srcPath = uri.getPath();
      MediaMetadataRetriever metaRetriever = new MediaMetadataRetriever();
      metaRetriever.setDataSource(srcPath);
      File file=new File(srcPath);
      float sizeInBytes = file.length();
      float sizeInMb = sizeInBytes / (1024 * 1024);
      if(sizeInMb>minimumFileSizeForCompress)
      {
        String destinationPath = generateCacheFilePath("mp4", reactContext);
        int actualHeight =Integer.parseInt(metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
        int actualWidth = Integer.parseInt(metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
        int bitrate = Integer.parseInt(metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE));

        float scale = actualWidth > actualHeight ? maxSize / actualWidth : maxSize / actualHeight;
        int resultWidth = Math.round(actualWidth * scale / 2) * 2;
        int resultHeight = Math.round(actualHeight * scale / 2) * 2;

        float videoBitRate = makeVideoBitrate(
          actualHeight, actualWidth,
          bitrate,
          resultHeight, resultWidth
        );
        compressVideo(srcPath, destinationPath, resultWidth, resultHeight,  videoBitRate,options.uuid,promise,reactContext);
      }
      else
      {
        promise.resolve(fileUrl);
      }

    } catch (Exception ex) {
      promise.reject(ex);
    }
  }

  public static int makeVideoBitrate(int originalHeight, int originalWidth, int originalBitrate, int height, int width) {
    float compressFactor = 0.8f;
    float  minCompressFactor = 0.8f;
    int maxBitrate = 1669_000;

    int remeasuredBitrate = (int) (originalBitrate / (Math.min(originalHeight / (float) (height), originalWidth / (float) (width))));
    remeasuredBitrate *= compressFactor;
    int minBitrate = (int) (getVideoBitrateWithFactor(minCompressFactor) / (1280f * 720f / (width * height)));
    if (originalBitrate < minBitrate) {
      return remeasuredBitrate;
    }
    if (remeasuredBitrate > maxBitrate) {
      return maxBitrate;
    }
    return Math.max(remeasuredBitrate, minBitrate);
  }
  private static int getVideoBitrateWithFactor(float f) {
    return (int) (f * 2000f * 1000f * 1.13f);
  }

  private static void sendEvent(ReactContext reactContext,
                                String eventName,
                                @Nullable WritableMap params) {
    reactContext
      .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
      .emit(eventName, params);
  }
}
