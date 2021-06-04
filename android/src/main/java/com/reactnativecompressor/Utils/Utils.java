package com.reactnativecompressor.Utils;

import com.facebook.react.bridge.ReactApplicationContext;

import java.io.File;
import java.util.UUID;

public class Utils {
  public static String generateCacheFilePath(String extension, ReactApplicationContext reactContext){
    File outputDir = reactContext.getCacheDir();

    String outputUri = String.format("%s/%s." + extension, outputDir.getPath(), UUID.randomUUID().toString());
    return outputUri;
  }
}
