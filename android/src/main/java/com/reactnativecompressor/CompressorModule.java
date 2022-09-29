package com.reactnativecompressor;

import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

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
import com.reactnativecompressor.Image.ImageCompressor;
import com.reactnativecompressor.Image.utils.ImageCompressorOptions;
import com.reactnativecompressor.Utils.Utils;
import com.reactnativecompressor.Video.VideoCompressorHelper;
import static com.reactnativecompressor.Utils.Utils.generateCacheFilePath;
import static com.reactnativecompressor.Utils.Utils.getRealPath;

import com.reactnativecompressor.Audio.AudioCompressor;

import java.io.File;

@ReactModule(name = CompressorModule.NAME)
public class CompressorModule extends ReactContextBaseJavaModule {
    public static final String NAME = "Compressor";
  private final ReactApplicationContext reactContext;
    public CompressorModule(ReactApplicationContext reactContext) {
        super(reactContext);
      this.reactContext = reactContext;
    }

    @Override
    @NonNull
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

    //Image
  @ReactMethod
  public void image_compress(
    String imagePath,
    ReadableMap optionMap,
    Promise promise) {
    try {
      imagePath=Utils.getRealPath(imagePath,reactContext);
      final ImageCompressorOptions options = ImageCompressorOptions.fromMap(optionMap);

      if(options.compressionMethod==ImageCompressorOptions.CompressionMethod.auto)
      {
        String returnableResult=ImageCompressor.autoCompressImage(imagePath,options,reactContext);
        promise.resolve(returnableResult);
      }
      else
      {
        String returnableResult=ImageCompressor.manualCompressImage(imagePath,options,reactContext);
        promise.resolve(returnableResult);
      }
    } catch (Exception ex) {
      promise.reject(ex);
    }
  }

  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  @ReactMethod
  public void compress_audio(
    String fileUrl,
    ReadableMap optionMap,
    Promise promise) {
    try{
      final VideoCompressorHelper options = VideoCompressorHelper.fromMap(optionMap);
      Uri uri= Uri.parse(fileUrl);
      String srcPath = uri.getPath();
      String destinationPath = generateCacheFilePath("mp3", reactContext);

      MediaMetadataRetriever metaRetriever = new MediaMetadataRetriever();
      metaRetriever.setDataSource(srcPath);

      float bitrate = options.bitrate;
      Log.d("nomi onStart", destinationPath+"onProgress: "+bitrate);
      new AudioCompressor().CompressAudio(srcPath, destinationPath, (int) bitrate*1000);

    } catch (Exception ex) {
      promise.reject(ex);
    }
  }


  //General
    @ReactMethod
   public void generateFilePath(String extension, Promise promise) {
     try {
       final String outputUri =generateCacheFilePath(extension,reactContext);
       promise.resolve(outputUri);
     } catch (Exception e) {
       promise.reject(e);
     }
   }

  @ReactMethod
  public void getRealPath(String path,String type, Promise promise) {
    try {
      final String realPath =Utils.getRealPath(path,reactContext);
      promise.resolve("file://"+realPath);
    } catch (Exception e) {
      promise.reject(e);
    }
  }

  @ReactMethod
  public void getVideoMetaData(String filePath, Promise promise) {
    try {
      filePath=Utils.getRealPath(filePath,reactContext);
      Uri uri= Uri.parse(filePath);
      String srcPath = uri.getPath();
      MediaMetadataRetriever metaRetriever = new MediaMetadataRetriever();
      metaRetriever.setDataSource(srcPath);
      File file=new File(srcPath);
      float sizeInKBs = file.length()/1024;
      int actualHeight =Integer.parseInt(metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
      int actualWidth = Integer.parseInt(metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
      long duration = Long.parseLong(metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
      String creationTime = metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE);
      String extension = filePath.substring(filePath.lastIndexOf(".")+1);

      WritableMap params = Arguments.createMap();
      params.putString("size", String.valueOf(sizeInKBs));
      params.putString("width", String.valueOf(actualWidth));
      params.putString("height", String.valueOf(actualHeight));
      params.putString("duration", String.valueOf(duration/1000));
      params.putString("extension", extension);
      params.putString("creationTime", String.valueOf(creationTime));

      promise.resolve(params);
    } catch (Exception e) {
      promise.reject(e);
    }
  }
}
