import Foundation
import AVFoundation

let videoCompressor = VideoCompressor()
let uploader=Uploader()
@objc(Compressor)
class Compressor: RCTEventEmitter {
    override static func moduleName() -> String {
        return "Compressor"
    }
    
    override init() {
        super.init()
        EventEmitterHandler.initCompressorInstance(self)
    }
    
    override func stopObserving() -> Void {
        EventEmitterHandler.stopObserving()
    }

    override func startObserving() -> Void {
        EventEmitterHandler.startObserving()
    }
    
    override static func requiresMainQueueSetup() -> Bool {
        return false
    }
    
    override func supportedEvents() -> [String] {
        return ["downloadProgress", "videoCompressProgress", "uploadProgress", "backgroundTaskExpired"]
    }

    @objc(image_compress:withOptions:withResolver:withRejecter:)
    func image_compress(_ imagePath: String, optionMap: NSDictionary, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        ImageMain.image_compress(imagePath, optionMap: optionMap, resolve: resolve, reject: reject)
    }
    
    @objc(getImageMetaData:withResolver:withRejecter:)
    func getImageMetaData(_ filePath: String, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        ImageMain.getImageMetaData(filePath,resolve: resolve,reject: reject)
    }
    
    @objc(compress_audio:withOptions:withResolver:withRejecter:)
    func compress_audio(_ fileUrl: String, optionMap: NSDictionary, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        AudioMain.compress_audio(fileUrl, optionMap: optionMap, resolve: resolve, reject: reject)
    }
    
    @objc(generateFilePath:withResolver:withRejecter:)
    func generateFilePath(_ _extension: String, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        Utils.generateFilePath(_extension, resolve: resolve, reject: reject)
    }
    
    @objc(getRealPath:withType:withResolver:withRejecter:)
    func getRealPath(_ path: String, type: String, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        Utils.getRealPath(path, type: type, resolve: resolve, reject: reject)
    }

    @objc(getFileSize:withResolver:withRejecter:)
    func getFileSize(_ filePath: String, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        Utils.getFileSize(filePath, resolve: resolve, reject: reject)
    }
    
    @objc(getVideoMetaData:withResolver:withRejecter:)
    func getVideoMetaData(_ filePath: String, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        videoCompressor.getVideoMetaData(filePath,resolve: resolve,reject: reject)
    }

    
    @objc(activateBackgroundTask:withResolver:withRejecter:)
    func activateBackgroundTask(options: [String: Any], resolve:@escaping RCTPromiseResolveBlock, reject:@escaping RCTPromiseRejectBlock) -> Void {
        videoCompressor.activateBackgroundTask(options: options,resolve: resolve,reject: reject)
    }

    @objc(deactivateBackgroundTask:withResolver:withRejecter:)
    func deactivateBackgroundTask(options: [String: Any], resolve:@escaping RCTPromiseResolveBlock, reject:@escaping RCTPromiseRejectBlock) -> Void {
        videoCompressor.deactivateBackgroundTask(options: options,resolve: resolve,reject: reject)
    }

    @objc(compress:withOptions:withResolver:withRejecter:)
    func compress(fileUrl: String, options: [String: Any], resolve:@escaping RCTPromiseResolveBlock, reject:@escaping RCTPromiseRejectBlock) -> Void {
        videoCompressor.compress(fileUrl: fileUrl, options: options, resolve: resolve, reject: reject)
    }
      
    @objc(cancelCompression:)
    func cancelCompression(uuid: String) -> Void {
      videoCompressor.cancelCompression(uuid: uuid)
    }

    @objc(upload:withOptions:withResolver:withRejecter:)
    func upload(filePath: String, options: [String: Any], resolve:@escaping RCTPromiseResolveBlock, reject:@escaping RCTPromiseRejectBlock) -> Void {
        uploader.upload(filePath: filePath, options: options, resolve: resolve, reject: reject)
    }
    
    @objc(cancelUpload:withShouldCancelAll:)
    func cancelUpload(uuid: String,shouldCancelAll:Bool) -> Void {
        uploader.cancelUpload(uuid: uuid,shouldCancelAll: shouldCancelAll)
    }
    
    @objc(download:withOptions:withResolver:withRejecter:)
    func download(filePath: String, options: [String: Any], resolve:@escaping RCTPromiseResolveBlock, reject:@escaping RCTPromiseRejectBlock) -> Void {
        Downloader.downloadFileAndSaveToCache(filePath, uuid: options["uuid"] as! String,progressDivider: options["progressDivider"] as? Int ?? 0) { downloadedPath in
            resolve(downloadedPath)
        }
    }
    
    @objc(createVideoThumbnail:withOptions:withResolver:withRejecter:)
    func createVideoThumbnail(fileUrl: String, options: NSDictionary, resolve:@escaping RCTPromiseResolveBlock, reject:@escaping RCTPromiseRejectBlock) -> Void {
        let videoThumbnail=CreateVideoThumbnail()
        videoThumbnail.create(fileUrl,options: options, resolve: resolve, rejecter: reject)
    }
    
    @objc(clearCache:withResolver:withRejecter:)
    func clearCache(cacheDir: String, resolve:@escaping RCTPromiseResolveBlock, reject:@escaping RCTPromiseRejectBlock) -> Void {
        CreateVideoThumbnail.cleanCacheDir(cacheDir: cacheDir,resolve: resolve,rejecter: reject)
    }
      
}
