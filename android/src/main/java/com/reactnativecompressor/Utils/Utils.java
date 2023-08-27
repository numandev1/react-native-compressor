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

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;

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
        String fileUrl="file://"+destinationPath;
        //convert finish,result(true is success,false is fail)
        promise.resolve(fileUrl);
        MediaCache.removeCompletedImagePath(fileUrl);
      }

      @Override
      public void onError(String errorMessage) {
        if(errorMessage.equals(("class java.lang.AssertionError")))
        {
          promise.resolve("file://"+srcPath);
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

  public static int getFileSizeFromURL(String urlString) {
    URL url;
    HttpURLConnection conn = null;
    try {
      url = new URL(urlString);
      conn = (HttpURLConnection) url.openConnection();
      conn.setRequestMethod("HEAD");
      conn.getInputStream();
      return conn.getContentLength();
    } catch (IOException e) {
      throw new RuntimeException(e);
    } finally {
      if (conn != null) {
        conn.disconnect();
      }
    }
  }

  public static String getRealPath(String fileUrl,ReactApplicationContext reactContext, String... args){
    if(fileUrl.startsWith("content://"))
    {
      try {
        Uri uri= Uri.parse(fileUrl);
        fileUrl= RealPathUtil.getRealPath(reactContext,uri);
      }
      catch (Exception ex) {
        Log.d(TAG, " Please see this issue: https://github.com/numandev1/react-native-compressor/issues/25");
      }
    }
    else if(fileUrl.startsWith("http://")||fileUrl.startsWith("https://"))
    {
      String uuid = (args.length > 0) ? args[0] : "";
      fileUrl=downloadMediaWithProgress(fileUrl,reactContext, uuid);
      Log.d(TAG, "getRealPath: "+fileUrl);
    }

    return fileUrl;
  }

  public static String downloadMediaWithProgress(String mediaUrl, ReactApplicationContext reactContext, String uuid) {
    downloadCompression[0]=0;
    OkHttpClient client = new OkHttpClient();

    Request request = new Request.Builder()
      .url(mediaUrl)
      .build();

    final Semaphore semaphore = new Semaphore(0); // Semaphore to wait for the download to complete
    final AtomicReference<String> filePathRef = new AtomicReference<>(null); // To store the file path

    // Perform the request asynchronously
    client.newCall(request).enqueue(new Callback() {
      @Override
      public void onResponse(Call call, Response response) throws IOException {
        if (response.isSuccessful()) {
          ResponseBody responseBody = response.body();
          if (responseBody != null) {
            String fileExtension = "unknown"; // Default extension

            // Detect the file extension based on the Content-Type header
            String contentType = response.header("Content-Type");
            if (contentType != null) {
              if (contentType.equals("image/jpeg")) {
                fileExtension = "jpg";
              } else if (contentType.equals("image/png")) {
                fileExtension = "png";
              } else if (contentType.equals("video/mp4")) {
                fileExtension = "mp4";
              }
            }

            File cacheDir = reactContext.getCacheDir();
            String randomFileName = UUID.randomUUID().toString() + "." + fileExtension;
            File mediaFile = new File(cacheDir, randomFileName);

            try (FileOutputStream fos = new FileOutputStream(mediaFile)) {
              BufferedInputStream inputStream = new BufferedInputStream(responseBody.byteStream());
              byte[] buffer = new byte[4096];
              int bytesRead;
              long totalBytesRead = 0;
              long totalBytes = responseBody.contentLength();

              if (totalBytes <= 0) {
                totalBytes = 31457280;
              }

              Log.d(TAG, totalBytesRead+" totalBytesRead "+totalBytes);
              Log.d(TAG, response.toString()+" responseBody "+responseBody.toString());
              while ((bytesRead = inputStream.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;
                double progressRatio = (double) totalBytesRead / totalBytes;
                int progress = (int) (progressRatio * 100);
                sendProgressUpdate(progress, uuid, reactContext);
              }
              fos.flush();
              String filePath="file://" + mediaFile.getAbsolutePath();
              MediaCache.addCompletedImagePath(filePath);
              filePathRef.set(filePath); // Set the file path
            } catch (IOException e) {
              e.printStackTrace();
              sendErrorResult(e.getMessage(), uuid, reactContext);
            } finally {
              semaphore.release(); // Release the semaphore when download is complete
            }
          }
        } else {
          sendErrorResult("Failed to download media: " + response.message(), uuid, reactContext);
          semaphore.release(); // Release the semaphore in case of error
        }
      }

      @Override
      public void onFailure(Call call, IOException e) {
        e.printStackTrace();
        sendErrorResult(e.getMessage(), uuid, reactContext);
        semaphore.release(); // Release the semaphore in case of failure
      }
    });

    try {
      semaphore.acquire(); // Wait for the download to complete
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    return filePathRef.get(); // Return the file path
  }
  static final int[] downloadCompression = {0};
  private static void sendProgressUpdate(int progress, String uuid, ReactApplicationContext reactContext) {
    int roundProgress=Math.round(progress);
    if(roundProgress%videoCompressionThreshold==0&&roundProgress> downloadCompression[0]) {
      WritableMap params = Arguments.createMap();
      WritableMap data = Arguments.createMap();
      params.putString("uuid", uuid);
      data.putDouble("progress", progress / 100.0);
      params.putMap("data", data);
      sendEvent(reactContext, "downloadProgress", params);
      Log.d(TAG, "downloadProgress: " + (progress / 100.0));
      downloadCompression[0] =roundProgress;
    }
  }

  private static void sendErrorResult(String error, String uuid, ReactApplicationContext reactContext) {
    WritableMap params = Arguments.createMap();
    WritableMap data = Arguments.createMap();
    params.putString("uuid", uuid);
    params.putString("error", error);
    params.putMap("data", data);
    Log.d(TAG, "videoDownloadError: "+error);
    sendEvent(reactContext, "videoDownloadError", params);
  }


}
