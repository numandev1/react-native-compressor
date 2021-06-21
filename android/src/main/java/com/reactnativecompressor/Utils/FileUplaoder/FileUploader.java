package com.reactnativecompressor.Utils.FileUplaoder;

import android.util.Log;

import androidx.annotation.Nullable;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableMapKeySetIterator;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.io.File;
import java.io.IOException;

import io.github.lizhangqu.coreprogress.ProgressHelper;
import io.github.lizhangqu.coreprogress.ProgressUIListener;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class FileUploader {
  private static final String TAG="asyncTaskFileUploader";


  private static void sendEvent(ReactContext reactContext,
                                String eventName,
                                @Nullable WritableMap params) {
    reactContext
      .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
      .emit(eventName, params);
  }

  private static void sendProgressEvent(long numBytes, long totalBytes, FileUploadHelper options, ReactApplicationContext reactContext) {
    WritableMap _params = Arguments.createMap();
    WritableMap _data = Arguments.createMap();
    _params.putString("uuid", options.uuid);
    _data.putDouble("written",  numBytes);
    _data.putDouble("total",  totalBytes);
    _params.putMap("data", _data);
    sendEvent(reactContext, "VideoCompressorProgress", _params);
  }

  public static void upload(String fileUrl, ReadableMap _options, ReactApplicationContext reactContext, Promise promise) {
    FileUploadHelper options= FileUploadHelper.fromMap(_options);
    String sourceFileUri = fileUrl;
    File uploadableFile = new File(sourceFileUri);
    String url = options.url;
    String contentType="video";

    OkHttpClient okHttpClient = new OkHttpClient();

    Request.Builder builder = new Request.Builder();
    builder.url(url);

    ReadableMapKeySetIterator headerIterator = options.headers.keySetIterator();
    while (headerIterator.hasNextKey()) {
      String key = headerIterator.nextKey();
      String value = options.headers.getString(key);
      Log.d(TAG, key+"  value: "+value);
      builder.addHeader(key, value);
      if(key.toLowerCase()=="content-type:")
      {
        contentType=value;
      }
    }

    MediaType mediaType = MediaType.parse(contentType);
    RequestBody body = RequestBody.create(mediaType, uploadableFile);

    RequestBody requestBody = ProgressHelper.withProgress(body, new ProgressUIListener() {

      //if you don't need this method, don't override this methd. It isn't an abstract method, just an empty method.
      @Override
      public void onUIProgressStart(long totalBytes) {
        super.onUIProgressStart(totalBytes);
        Log.d(TAG, "onUIProgressStart:" + totalBytes);
      }

      @Override
      public void onUIProgressChanged(long numBytes, long totalBytes, float percent, float speed) {
        sendProgressEvent(numBytes,totalBytes,options,reactContext);
        Log.d(TAG, "=============start===============");
        Log.d(TAG, "numBytes:" + numBytes);
        Log.d(TAG, "totalBytes:" + totalBytes);
        Log.d(TAG, "percent:" + percent);
        Log.d(TAG, "speed:" + speed);
        Log.d(TAG, "============= end ===============");
      }

      //if you don't need this method, don't override this methd. It isn't an abstract method, just an empty method.
      @Override
      public void onUIProgressFinish() {
        super.onUIProgressFinish();
        Log.d(TAG, "onUIProgressFinish:");
      }
    });
    builder.put(requestBody);

    Call call = okHttpClient.newCall(builder.build());
    call.enqueue(new Callback() {
      @Override
      public void onFailure(Call call, IOException e) {
        Log.d(TAG, "=============onFailure===============");
        promise.reject("");
        e.printStackTrace();
      }

      @Override
      public void onResponse(Call call, Response response) throws IOException {
        Log.d(TAG, "=============onResponse===============");
        Log.d(TAG, "request headers:" + response.request().headers());
        Log.d(TAG, "response code:" + response.code());
        Log.d(TAG, "response headers:" + response.headers());
        Log.d(TAG, "response body:" + response.body().string());
        WritableMap param = Arguments.createMap();
        param.putInt("status",response.code());
        promise.resolve(param);
      }
    });
  }
}
