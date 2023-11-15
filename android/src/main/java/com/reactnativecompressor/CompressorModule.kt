package com.reactnativecompressor

import android.os.Build
import androidx.annotation.RequiresApi
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
import com.reactnativecompressor.Audio.AudioMain
import com.reactnativecompressor.Image.ImageMain
import com.reactnativecompressor.Utils.CreateVideoThumbnailClass
import com.reactnativecompressor.Utils.Downloader
import com.reactnativecompressor.Utils.EventEmitterHandler
import com.reactnativecompressor.Utils.Uploader
import com.reactnativecompressor.Utils.Utils
import com.reactnativecompressor.Utils.Utils.generateCacheFilePath
import com.reactnativecompressor.Utils.Utils.getRealPath
import com.reactnativecompressor.Video.VideoMain

class CompressorModule(private val reactContext: ReactApplicationContext) : CompressorSpec(reactContext) {
  private val imageMain: ImageMain = ImageMain(reactContext)
  private val videoMain: VideoMain = VideoMain(reactContext)
  private val audioMain: AudioMain = AudioMain(reactContext)
  private val uploader: Uploader = Uploader(reactContext)
  private val videoThumbnail: CreateVideoThumbnailClass = CreateVideoThumbnailClass(reactContext)

  override fun initialize() {
    super.initialize()
    EventEmitterHandler.reactContext=reactContext;
  }


  companion object {
    const val NAME = "Compressor"
  }
  override fun getName(): String {
        return NAME
  }

    //Image
    @ReactMethod
    override fun image_compress(
            imagePath: String,
            optionMap: ReadableMap,
            promise: Promise) {
      imageMain.image_compress(imagePath,optionMap,promise)
    }

  @ReactMethod
  override fun getImageMetaData(filePath: String, promise: Promise) {
    imageMain.getImageMetaData(filePath,promise)
  }

  // VIdeo
  @ReactMethod
  override fun compress(
    fileUrl: String,
    optionMap: ReadableMap,
    promise: Promise) {
    videoMain.compress(fileUrl,optionMap,promise)
  }

  @ReactMethod
  override fun cancelCompression(
    uuid: String) {
    videoMain.cancelCompression(uuid)
  }

  @ReactMethod
  override fun activateBackgroundTask(
    options: ReadableMap,
    promise: Promise) {
    videoMain.activateBackgroundTask(options,promise)
  }

  @ReactMethod
  override fun deactivateBackgroundTask(
    options: ReadableMap,
    promise: Promise) {
    videoMain.deactivateBackgroundTask(options,promise)
  }

 // Audio
    @ReactMethod
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    override fun compress_audio(
            fileUrl: String,
            optionMap: ReadableMap,
            promise: Promise) {
   audioMain.compress_audio(fileUrl,optionMap,promise)
    }

    // Others
    @ReactMethod
    override fun generateFilePath(_extension: String, promise: Promise) {
        try {
            val outputUri = generateCacheFilePath(_extension, reactContext)
            promise.resolve(outputUri)
        } catch (e: Exception) {
            promise.reject(e)
        }
    }

    @ReactMethod
    override fun getRealPath(path: String, type: String, promise: Promise) {
        try {
            val realPath = getRealPath(path, reactContext)
            promise.resolve("file://$realPath")
        } catch (e: Exception) {
            promise.reject(e)
        }
    }

    @ReactMethod
    override fun getVideoMetaData(filePath: String, promise: Promise) {
      videoMain.getVideoMetaData(filePath,promise)
    }

    @ReactMethod
    override fun getFileSize(filePath: String, promise: Promise) {
      Utils.getFileSize(filePath,promise,reactContext)
    }

  @ReactMethod
  override fun upload(
    fileUrl: String,
    options: ReadableMap,
    promise: Promise) {
    uploader.upload(fileUrl, options, reactContext, promise)
  }

  @ReactMethod
  override fun cancelUpload(uuid: String,shouldCancelAll:Boolean) {
    uploader.cancelUpload(uuid,shouldCancelAll)
  }

  @ReactMethod
  override fun download(
    fileUrl: String,
    options: ReadableMap,
    promise: Promise) {
    var uuid: String = ""
    var progressDivider=0;
    if(options.hasKey("uuid"))
    {
      uuid= options.getString("uuid") as String
    }
    if(options.hasKey("progressDivider"))
    {
      progressDivider= options.getString("progressDivider") as Int
    }
    val downloadedFilePath:String?=Downloader.downloadMediaWithProgress(fileUrl, uuid,progressDivider,reactContext)
    if(downloadedFilePath!=null)
    {
      promise.resolve(downloadedFilePath)
    }
    else
    {
      promise.reject("Unable to download")
    }
  }

  @ReactMethod
  override fun createVideoThumbnail(fileUrl:String, options:ReadableMap, promise:Promise) {
    videoThumbnail.create(fileUrl,options,promise)
  }

  @ReactMethod
  override fun clearCache(cacheDir:String?, promise:Promise) {
    CreateVideoThumbnailClass.clearCache(cacheDir, promise, reactContext)
  }

    @ReactMethod
    override fun addListener(eventName: String) {
    }

    @ReactMethod
    override fun removeListeners(count: Double) {
    }
}
