package com.reactnativecompressor.Image

import com.facebook.react.bridge.ReadableMap

class ImageCompressorOptions {
    enum class InputType {
        base64,
        uri
    }

    enum class OutputType {
        png,
        jpg
    }

    enum class ReturnableOutputType {
        base64,
        uri
    }

    enum class CompressionMethod {
        auto,
        manual
    }

    var compressionMethod = CompressionMethod.auto
    var maxWidth = 1280
    var maxHeight = 1280
    var progressDivider: Int? = 0
    var quality = 0.8f
    var input = InputType.uri
    var output = OutputType.jpg
    var uuid: String? = ""
    var returnableOutputType = ReturnableOutputType.uri
    var disablePngTransparency:Boolean = false

    companion object {
        fun fromMap(map: ReadableMap): ImageCompressorOptions {
            val options = ImageCompressorOptions()
            val iterator = map.keySetIterator()
            while (iterator.hasNextKey()) {
                val key = iterator.nextKey()
                when (key) {
                    "compressionMethod" -> options.compressionMethod = CompressionMethod.valueOf(map.getString(key)!!)
                    "maxWidth" -> options.maxWidth = map.getInt(key)
                    "maxHeight" -> options.maxHeight = map.getInt(key)
                    "progressDivider" -> options.progressDivider = map.getInt(key)
                    "quality" -> options.quality = map.getDouble(key).toFloat()
                    "input" -> options.input = InputType.valueOf(map.getString(key)!!)
                    "output" -> options.output = OutputType.valueOf(map.getString(key)!!)
                    "returnableOutputType" -> options.returnableOutputType = ReturnableOutputType.valueOf(map.getString(key)!!)
                    "uuid" -> options.uuid = map.getString(key)
                    "disablePngTransparency" -> options.disablePngTransparency = map.getBoolean(key)
                }
            }
            return options
        }
    }
}
