//
//  EventEmitterHandler.swift
//  react-native-compressor
//
//  Created by Numan on 09/09/2023.
//

import Foundation

class EventEmitterHandler {
    static var sharedCompressorObject: Any!
    static var hasListener: Bool=false
    
    static func initCompressorInstance(_ object: Any) {
        sharedCompressorObject = object
    }
    
    
    static func stopObserving() -> Void {
      hasListener = false
    }

    static func startObserving() -> Void {
      hasListener = true
    }
    
    static func emitDownloadProgress(_ progress: NSNumber, uuid: String) {
        var params = [String: Any]()
        var data = [String: Any]()
        params["uuid"] = uuid
        data["progress"] = progress
        params["data"] = data
        
        (sharedCompressorObject as AnyObject).sendEvent(withName: "downloadProgress", body: params)
    }
    
    static func emitVideoCompressProgress(_ progress: Float, uuid: String) {
        if(self.hasListener){
            (sharedCompressorObject as AnyObject).sendEvent(withName: "videoCompressProgress", body: ["uuid": uuid, "data": ["progress": progress]])
        }
    }
    
    static func emituploadProgress(_ uuid: String, totalBytesSent: Int64, totalBytesExpectedToSend: Int64) {
        if(self.hasListener){
            (sharedCompressorObject as AnyObject).sendEvent(withName: "uploadProgress", body: ["uuid": uuid, "data": ["written": totalBytesSent, "total": totalBytesExpectedToSend]])
        }
    }
    
    static func emitBackgroundTaskExpired(_ backgroundTaskId:UIBackgroundTaskIdentifier) {
        if(self.hasListener){
            (sharedCompressorObject as AnyObject).sendEvent(withName: "backgroundTaskExpired", body: ["backgroundTaskId": backgroundTaskId])
        }
    }
}
