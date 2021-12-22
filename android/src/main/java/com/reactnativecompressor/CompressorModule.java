package com.reactnativecompressor;

import android.graphics.Bitmap;
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
import com.reactnativecompressor.Video.VideoCompressorHelper;
import com.reactnativecompressor.Video.videoslimmer.VideoSlimmer;

import static com.reactnativecompressor.Utils.Utils.generateCacheFilePath;
import java.io.ByteArrayOutputStream;
import com.reactnativecompressor.Audio.AudioCompressor;

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
      new AudioCompressor().CompressAudio(srcPath, destinationPath, (int) bitrate*1000, new VideoSlimmer.ProgressListener() {


        @Override
        public void onStart() {
          //convert start
          Log.d("nomi onStart", "onProgress: ");

        }

        @Override
        public void onFinish(boolean result) {
          //convert finish,result(true is success,false is fail)
          promise.resolve(destinationPath);
          Log.d("nomi onFinish", "onProgress: ");
        }

        @Override
        public void onError(String  errorMessage) {

        }

        @Override
        public void onProgress(float percent) {
          WritableMap params = Arguments.createMap();
          WritableMap data = Arguments.createMap();
          params.putString("uuid", options.uuid);
          data.putDouble("progress",  percent/100);
          params.putMap("data", data);
          Log.d("nomi onProgress", "onProgress: "+percent);
          sendEvent(reactContext, "videoCompressProgress", params);
        }
      });

    } catch (Exception ex) {
      promise.reject(ex);
    }
  }


  //General
    @ReactMethod
   public void generateFile(String extension, Promise promise) {
     try {
       final String outputUri =generateCacheFilePath(extension,reactContext);
       promise.resolve(outputUri);
     } catch (Exception e) {
       promise.reject(e);
     }
   }
}
