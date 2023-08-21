package com.reactnativecompressor;

import androidx.annotation.NonNull;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactMethod;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.reactnativecompressor.Image.ImageCompressor;
import com.reactnativecompressor.Image.utils.ImageCompressorOptions;
import com.reactnativecompressor.Utils.Utils;
import com.reactnativecompressor.Video.VideoCompressorHelper;
import com.reactnativecompressor.CompressorSpec;
import static com.reactnativecompressor.Utils.Utils.generateCacheFilePath;

import android.content.ContentResolver;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.provider.OpenableColumns;
import android.util.Log;

import com.reactnativecompressor.Audio.AudioCompressor;

import java.io.File;
import java.io.FileNotFoundException;

public class CompressorModule extends CompressorSpec {
  public static final String NAME = "Compressor";
  private final ReactApplicationContext reactContext;

  CompressorModule(ReactApplicationContext context) {
    super(context);
    this.reactContext = context;
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

  @ReactMethod
  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
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
  @ReactMethod
  public void getFileSize(String filePath, Promise promise)
  {
    filePath=Utils.getRealPath(filePath,reactContext);
    Uri uri= Uri.parse(filePath);
    ContentResolver contentResolver = reactContext.getContentResolver();
    long fileSize = getLength(uri, contentResolver);
    if (fileSize >= 0) {
      promise.resolve(String.valueOf(fileSize));
    } else {
      promise.resolve("");
    }
  }

  public static long getLength(Uri uri, ContentResolver contentResolver) {
    AssetFileDescriptor assetFileDescriptor = null;
    try {
      assetFileDescriptor = contentResolver.openAssetFileDescriptor(uri, "r");
    } catch (FileNotFoundException e) {
      // Do nothing
    }

    long length = (assetFileDescriptor != null) ? assetFileDescriptor.getLength() : -1L;
    if (length != -1L) {
      return length;
    }

    if (ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
      Cursor cursor = contentResolver.query(uri, new String[]{OpenableColumns.SIZE}, null, null, null);
      if (cursor != null) {
        try {
          int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
          if (sizeIndex != -1 && cursor.moveToFirst()) {
            try {
              return cursor.getLong(sizeIndex);
            } catch (Throwable ignored) {
              return -1L;
            }
          }
        } finally {
          cursor.close();
        }
      }
      return -1L;
    } else {
      return -1L;
    }
  }
}
