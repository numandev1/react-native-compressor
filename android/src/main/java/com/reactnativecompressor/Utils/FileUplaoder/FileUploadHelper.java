package com.reactnativecompressor.Utils.FileUplaoder;

import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableMapKeySetIterator;

public class FileUploadHelper {
  public static FileUploadHelper fromMap(ReadableMap map) {
    final FileUploadHelper options = new FileUploadHelper();
    final ReadableMapKeySetIterator iterator = map.keySetIterator();

    while (iterator.hasNextKey()) {
      final String key = iterator.nextKey();

      switch (key) {
        case "uuid":
          options.uuid = map.getString(key);
          break;

        case "method":
          options.method = map.getString(key);
          break;

        case "headers":
          options.headers = map.getMap(key);
          break;

        case "url":
          options.url = map.getString(key);
          break;

      }
    }
    return options;
  }
  public String uuid;
  public String method;
  public ReadableMap headers;
  public String url;
}
