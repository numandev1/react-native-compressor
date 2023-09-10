package com.reactnativecompressor.Utils

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.modules.core.DeviceEventManagerModule

class EventEmitterHandler {
  companion object {
    public var reactContext: ReactApplicationContext?=null

    private fun sendEvent(eventName: String,
                          params: Any?) {
      reactContext
        ?.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
        ?.emit(eventName, params)
    }

    fun emitBackgroundTaskExpired(backgroundId: String?){
      sendEvent("backgroundTaskExpired",backgroundId)
    }

    fun emitVideoCompressProgress(progress:Double,uuid:String){
      val params = Arguments.createMap()
      val data = Arguments.createMap()
      params.putString("uuid", uuid)
      data.putDouble("progress", progress)
      params.putMap("data", data)
      sendEvent("videoCompressProgress", params)
    }

    fun emitDownloadProgress(progress:Double,uuid:String){
      val params = Arguments.createMap()
      val data = Arguments.createMap()
      params.putString("uuid", uuid)
      data.putDouble("progress", progress)
      params.putMap("data", data)
      sendEvent("downloadProgress", params)
    }

    fun emitDownloadProgressError(uuid:String?, error:String?){
      if (uuid != null && error != null) {
        val params = Arguments.createMap()
        val data = Arguments.createMap()
        params.putString("uuid", uuid)
        params.putString("error", error)
        params.putMap("data", data)
        sendEvent("downloadProgressError", params)
      }
    }

    fun sendUploadProgressEvent(numBytes: Long, totalBytes: Long, uuid:String?) {
      if(uuid!=null) {
        val _params = Arguments.createMap()
        val _data = Arguments.createMap()
        _params.putString("uuid", uuid)
        _data.putDouble("written", numBytes.toDouble())
        _data.putDouble("total", totalBytes.toDouble())
        _params.putMap("data", _data)
        sendEvent("uploadProgress", _params)
      }
    }


  }

}
