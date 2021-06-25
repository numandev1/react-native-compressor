package com.reactnativecompressor.Image.utils;

import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableMapKeySetIterator;

public class ImageCompressorOptions {
    public static ImageCompressorOptions fromMap(ReadableMap map) {
        final ImageCompressorOptions options = new ImageCompressorOptions();
        final ReadableMapKeySetIterator iterator = map.keySetIterator();

        while (iterator.hasNextKey()) {
            final String key = iterator.nextKey();

            switch (key) {
                case "compressionMethod":
                     options.compressionMethod = CompressionMethod.valueOf(map.getString(key));
                      break;
                case "maxWidth":
                    options.maxWidth = map.getInt(key);
                    break;
                case "maxHeight":
                    options.maxHeight = map.getInt(key);
                    break;
                case "quality":
                    options.quality = (float) map.getDouble(key);
                    break;
                case "input":
                    options.input = InputType.valueOf(map.getString(key));
                    break;
                case "output":
                    options.output = OutputType.valueOf(map.getString(key));
                    break;
              case "returnableOutputType":
                options.returnableOutputType = ReturnableOutputType.valueOf(map.getString(key));
                break;
            }
        }

        return options;
    }

    public enum InputType {
        base64, uri
    }

    public enum OutputType {
        png, jpg
    }

  public enum ReturnableOutputType {
    base64, uri
  }

  public enum CompressionMethod {
    auto, manual
  }

    public CompressionMethod compressionMethod = CompressionMethod.manual;
    public int maxWidth = 1280;
    public int maxHeight = 1280;
    public float quality = 0.8f;
    public InputType input = InputType.uri;
    public OutputType output = OutputType.jpg;
    public ReturnableOutputType returnableOutputType = ReturnableOutputType.uri;
}
