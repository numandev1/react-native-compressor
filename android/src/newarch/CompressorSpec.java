package com.reactnativecompressor;

import com.facebook.react.bridge.ReactApplicationContext;

abstract class CompressorSpec extends NativeCompressorSpec {
  CompressorSpec(ReactApplicationContext context) {
    super(context);
  }
}
