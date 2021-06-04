package com.reactnativecompressor.Utils.FileUplaoder;

import android.os.AsyncTask;
import android.util.Log;

import androidx.annotation.Nullable;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.reactnativecompressor.Utils.FileUplaoder.FileUploadHelper;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class FileUploader extends AsyncTask<String, Void, String> {
  private String fileUrl;
  private final FileUploadHelper options;
  private  ReactApplicationContext reactContext;
  public FileUploader(String fileUrl, ReadableMap options,ReactApplicationContext reactContext){
    this.fileUrl=fileUrl;
     this.options= FileUploadHelper.fromMap(options);
     this.reactContext=reactContext;
  }

  private void sendEvent(ReactContext reactContext,
                         String eventName,
                         @Nullable WritableMap params) {
    reactContext
      .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
      .emit(eventName, params);
  }

  @Override
  protected String doInBackground(String... params) {

    try {
      String sourceFileUri = fileUrl;

      HttpURLConnection conn = null;
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

          // open a URL connection to the Servlet
          FileInputStream fileInputStream = new FileInputStream(
            sourceFile);
          URL url = new URL(upLoadServerUri);

          // Open a HTTP connection to the URL
          conn = (HttpURLConnection) url.openConnection();
          conn.setDoInput(true); // Allow Inputs
          conn.setDoOutput(true); // Allow Outputs
          conn.setUseCaches(false); // Don't use a Cached Copy
          conn.setRequestMethod(this.options.method);
          conn.setDoOutput(true);
          conn.setRequestProperty("Connection", "Keep-Alive");
          conn.setRequestProperty("file", sourceFileUri);


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
              WritableMap _params = Arguments.createMap();
              WritableMap _data = Arguments.createMap();
              _params.putString("uuid", options.uuid);
              _data.putDouble("progress",  progress);
              _params.putMap("data", _data);
              sendEvent(reactContext, "videoCompressProgress", _params);
            }
          }


          String serverResponseMessage = conn
            .getResponseMessage();

          if (conn.getResponseCode() == 200) {
            Log.d("uplaodFile", "doInBackground: upload successfully");
            WritableMap _params = Arguments.createMap();
            WritableMap _data = Arguments.createMap();
            _params.putString("uuid", options.uuid);
            _data.putDouble("progress",  1);
            _params.putMap("data", _data);
            sendEvent(reactContext, "videoCompressProgress", _params);
          }

          fileInputStream.close();
          dos.flush();
          dos.close();

        } catch (Exception e) {

          e.printStackTrace();

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
