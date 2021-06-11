package com.reactnativecompressor.Utils.FileUplaoder;

import android.os.AsyncTask;
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

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.cert.X509Certificate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class FileUploader extends AsyncTask<String, Void, String> {
  private Promise promise;
  private String fileUrl;
  private final FileUploadHelper options;
  private  final ReactApplicationContext reactContext;
  private  final String TAG="asyncTaskFileUploader";
  public FileUploader(String fileUrl, ReadableMap options,ReactApplicationContext reactContext,Promise promise){
    this.fileUrl=fileUrl;
     this.options= FileUploadHelper.fromMap(options);
     this.reactContext=reactContext;
     this.promise=promise;
  }

  private void sendEvent(ReactContext reactContext,
                         String eventName,
                         @Nullable WritableMap params) {
    reactContext
      .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
      .emit(eventName, params);
  }

  private void sendProgressEvent(float progress) {
    WritableMap _params = Arguments.createMap();
    WritableMap _data = Arguments.createMap();
    _params.putString("uuid", options.uuid);
    _data.putDouble("written",  progress*100);
    _data.putDouble("total",  100);
    _params.putMap("data", _data);
    sendEvent(this.reactContext, "VideoCompressorProgress", _params);
  }

  @Override
  protected String doInBackground(String... params) {
    try {

      TrustManager[] trustAllCerts = new TrustManager[] {
              new X509TrustManager() {
                public java.security.cert.X509Certificate[] getAcceptedIssuers()
                {
                  return null;
                }
                public void checkClientTrusted(X509Certificate[] certs, String authType)
                {
                  //
                }
                public void checkServerTrusted(X509Certificate[] certs, String authType)
                {
                  //
                }
              }
      };
      SSLContext sc = SSLContext.getInstance("TLS");
      sc.init(null, trustAllCerts, new java.security.SecureRandom());
      HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

      String sourceFileUri = fileUrl;

      HttpsURLConnection conn = null;
      DataOutputStream dos = null;
      String lineEnd = "\r\n";
      String twoHyphens = "--";
      String boundary = "*****";
      int bytesRead, bytesAvailable, bufferSize;
      byte[] buffer;
      int maxBufferSize = 1 * 1024 * 1024;
      File sourceFile = new File(sourceFileUri);

      if (sourceFile.isFile()) {

        try {
          String upLoadServerUri =this.options.url;

          FileInputStream fileInputStream = new FileInputStream(
            sourceFile);
          URL url = new URL(upLoadServerUri);
          conn = (HttpsURLConnection) url.openConnection();
          ReadableMapKeySetIterator headerIterator = this.options.headers.keySetIterator();
          while (headerIterator.hasNextKey()) {
            String key = headerIterator.nextKey();
            String value = this.options.headers.getString(key);
            Log.d(TAG, key+"  value: "+value);
            conn.setRequestProperty(key, value);
          }
          conn.setUseCaches(false); // Don't use a Cached Copy
          conn.setRequestMethod(this.options.method);
          conn.connect();
            dos = new DataOutputStream(conn.getOutputStream());

          dos.writeBytes(twoHyphens + boundary + lineEnd);

          dos.writeBytes(lineEnd);

          // create a buffer of maximum size
          bytesAvailable = fileInputStream.available();

          bufferSize = Math.min(bytesAvailable, maxBufferSize);
          buffer = new byte[bufferSize];

          // read file and write it into form...
          bytesRead = fileInputStream.read(buffer, 0, bufferSize);


          //
          byte data[] = new byte[1024];
          long total = 0;
          int count;

          while (bytesRead > 0) {
            bytesAvailable = fileInputStream.available();
            bufferSize = Math
              .min(bytesAvailable, maxBufferSize);
            bytesRead = fileInputStream.read(buffer, 0,
              bufferSize);
            while ((count = fileInputStream.read(data)) != -1) {
              total += count;
              float progress=Float.parseFloat(String.valueOf(total)) /Float.parseFloat(String.valueOf(bytesAvailable));
                sendProgressEvent(progress);
            }
          }


          String serverResponseMessage = conn
            .getResponseMessage();

          if (conn.getResponseCode() == 200) {
            Log.d(TAG, "doInBackground: upload successfully");
            sendProgressEvent(1);
            WritableMap param = Arguments.createMap();
            param.putInt("status",conn.getResponseCode());
            this.promise.resolve(param);
          }
          else
          {
            Log.d(TAG, serverResponseMessage+" doInBackground: error "+conn.getResponseCode());
            this.promise.reject("");
          }

          fileInputStream.close();
          dos.flush();
          dos.close();

        } catch (Exception e) {

          e.printStackTrace();

        }
        finally {
          if (conn != null)
            conn.disconnect();
        }
      }


    } catch (Exception ex) {
      ex.printStackTrace();
    }
    return "Executed";
  }

  @Override
  protected void onPostExecute(String result) {

  }

  @Override
  protected void onPreExecute() {
  }

  @Override
  protected void onProgressUpdate(Void... values) {
  }
}
