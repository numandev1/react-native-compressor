package com.reactnativecompressor.Audio

import com.facebook.react.bridge.ReadableMap

class AudioHelper {

  var quality: String? = "medium"
  var progressDivider: Int? = 0

  companion object {
    fun fromMap(map: ReadableMap): AudioHelper {
      val options = AudioHelper()
      val iterator = map.keySetIterator()
      while (iterator.hasNextKey()) {
        val key = iterator.nextKey()
        when (key) {
          "quality" -> options.quality = map.getString(key)
        }
      }
      return options
    }
  }
}
