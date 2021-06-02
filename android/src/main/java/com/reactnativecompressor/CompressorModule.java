package com.reactnativecompressor;

import android.graphics.Bitmap;

import androidx.annotation.NonNull;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.module.annotations.ReactModule;
import com.reactnativecompressor.Image.ImageCompressor;
import com.reactnativecompressor.Image.utils.ImageCompressorOptions;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.UUID;
import static com.reactnativecompressor.Video.VideoCompressor.*;

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


  //Video
  @ReactMethod
  public void video_compress(
    String fileUrl,
    ReadableMap options,
    Promise promise) {
    try {
      String response=video_compress_helper(fileUrl,options);
      promise.resolve(response);
    } catch (Exception ex) {
      promise.reject(ex);
    }
  }

  @ReactMethod
  public void video_upload(
    String fileUrl,
    ReadableMap options,
    Promise promise) {
    try {

      String response=video_upload_helper(fileUrl,options);
      promise.resolve(response);
    } catch (Exception ex) {
      promise.reject(ex);
    }
  }

  @ReactMethod
  public void video_activateBackgroundTask(
    ReadableMap options,
    Promise promise) {
    try {
      String response=video_activateBackgroundTask_helper(options);
      promise.resolve(response);
    } catch (Exception ex) {
      promise.reject(ex);
    }
  }

  @ReactMethod
  public void video_deactivateBackgroundTask(
    ReadableMap options,
    Promise promise) {
    try {
      String response=video_deactivateBackgroundTask_helper(options);
      promise.resolve(response);
    } catch (Exception ex) {
      promise.reject(ex);
    }
  }

    //Image
  @ReactMethod
  public void image_compress(
    String value,
    ReadableMap optionMap,
    Promise promise) {
    try {
      final ImageCompressorOptions options = ImageCompressorOptions.fromMap(optionMap);
      final Bitmap image = options.input == ImageCompressorOptions.InputType.base64
        ? ImageCompressor.decodeImage(value)
        : ImageCompressor.loadImage(value);

      final Bitmap resizedImage = ImageCompressor.resize(image, options.maxWidth, options.maxHeight);
      final ByteArrayOutputStream imageDataByteArrayOutputStream = ImageCompressor.compress(resizedImage, options.output, options.quality);
      Boolean isBase64=options.returnableOutputType==ImageCompressorOptions.ReturnableOutputType.base64;

      final String returnableResult = ImageCompressor.encodeImage(imageDataByteArrayOutputStream,isBase64,image,options.output.toString(),this.reactContext);

      promise.resolve(returnableResult);
    } catch (Exception ex) {
      promise.reject(ex);
    }
  }


  //General
    @ReactMethod
   public void generateFile(String extension, Promise promise) {
     try {
       final String outputUri =ImageCompressor.generateCacheFilePath(extension,reactContext);
       promise.resolve(outputUri);
     } catch (Exception e) {
       promise.reject(e);
     }
   }
}
