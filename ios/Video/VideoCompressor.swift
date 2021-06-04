import Foundation
import AssetsLibrary
import AVFoundation
import NextLevelSessionExporter

struct CompressionError: Error {
  private let message: String

  var localizedDescription: String {
    return message
  }
  
  init(message: String) {
    self.message = message
  }
}

struct UploadError: Error {
  private let message: String

  var localizedDescription: String {
    return message
  }
  
  init(message: String) {
    self.message = message
  }
}

@available(iOS 11.0, *)
@objc(VideoCompressor)
class VideoCompressor: RCTEventEmitter, URLSessionTaskDelegate {
  var backgroundTaskId: UIBackgroundTaskIdentifier = .invalid;
  var hasListener: Bool=false
  var uploadResolvers: [String: RCTPromiseResolveBlock] = [:]
  var uploadRejectors: [String: RCTPromiseRejectBlock] = [:]

  override static func requiresMainQueueSetup() -> Bool {
    return false
  }

  @objc(activateBackgroundTask:withResolver:withRejecter:)
  func activateBackgroundTask(options: [String: Any], resolve:@escaping RCTPromiseResolveBlock, reject:@escaping RCTPromiseRejectBlock) -> Void {
    guard backgroundTaskId == .invalid else {
      reject("failed", "There is a background task already", nil)
      return
    }
    backgroundTaskId = UIApplication.shared.beginBackgroundTask(
      withName: "video-upload",
      expirationHandler: {
        self.sendEvent(withName: "backgroundTaskExpired", body: ["backgroundTaskId": self.backgroundTaskId])
        UIApplication.shared.endBackgroundTask(self.backgroundTaskId)
        self.backgroundTaskId = .invalid
    })
    resolve(backgroundTaskId)
  }

  @objc(deactivateBackgroundTask:withResolver:withRejecter:)
  func deactivateBackgroundTask(options: [String: Any], resolve:@escaping RCTPromiseResolveBlock, reject:@escaping RCTPromiseRejectBlock) -> Void {
    guard backgroundTaskId != .invalid else {
      reject("failed", "There is no active background task", nil)
        return
    }
    UIApplication.shared.endBackgroundTask(backgroundTaskId)
    resolve(nil)
    backgroundTaskId = .invalid
  }

  @objc(compress:withOptions:withResolver:withRejecter:)
  func compress(fileUrl: String, options: [String: Any], resolve:@escaping RCTPromiseResolveBlock, reject:@escaping RCTPromiseRejectBlock) -> Void {
    compressVideo(url: URL(string: fileUrl)!, bitRate: options["bitrate"] as! Float?,
    onProgress: { progress in 
      print("Progress", progress)
      if(self.hasListener){
        self.sendEvent(withName: "videoCompressProgress", body: ["uuid": options["uuid"], "data": ["progress": progress]])
      }
    }, onCompletion: { newUrl in
      resolve("\(newUrl)");
    }, onFailure: { error in
      reject("failed", "Compression Failed", error)
    })
  }

  @objc(upload:withOptions:withResolver:withRejecter:)
  func upload(fileUrl: String, options: [String: Any], resolve:@escaping RCTPromiseResolveBlock, reject:@escaping RCTPromiseRejectBlock) -> Void {
    guard let uuid = options["uuid"] as? String else {
      let uploadError = UploadError(message: "UUID is missing")
      reject("Upload Failed", "UUID is missing", uploadError)
      return
    }

    guard let remoteUrl = options["url"] as? String else {
      let uploadError = UploadError(message: "url is missing")
      reject("Upload Failed", "url is missing", uploadError)
      return
    }

    guard let method = options["method"] as? String else {
      let uploadError = UploadError(message: "method is missing")
      reject("Upload Failed", "method is missing", uploadError)
      return
    }

    guard let file = URL(string: fileUrl) else{
      let uploadError = UploadError(message: "invalid file url")
      reject("Failed", "Upload Failed", uploadError)
      return
    }

    let headers = options["headers"] as? [String: String] ?? [:]

    let url = URL(string: remoteUrl)!
    var request = URLRequest(url: url)
    request.httpMethod=method
    for(header, v) in headers{
      request.setValue(v, forHTTPHeaderField: header)
    }

    uploadResolvers[uuid] = resolve
    uploadRejectors[uuid] = reject
    // TODO: ADD Headers
    let config = URLSessionConfiguration.background(withIdentifier: uuid)
    let session = URLSession(configuration: config, delegate: self, delegateQueue: nil)
    let task = session.uploadTask(with: request, fromFile: file)
    task.resume()
  }

  func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
    guard let uuid = session.configuration.identifier else {return}
    guard let reject = uploadRejectors[uuid] else{return}
    guard let resolve = uploadResolvers[uuid] else{return}
    guard error == nil else {
      reject("failed", "Upload Failed", error)
      uploadRejectors[uuid] = nil
      return;
    }

    guard let response = task.response  as? HTTPURLResponse else {
      let uploadError = UploadError(message: "Response is not defined")
      reject("failed", "Upload Failed", uploadError)
      uploadRejectors[uuid] = nil
      return;
    }

    let result: [String : Any] = ["status": response.statusCode, "headers": response.allHeaderFields, "body": ""]
    
    resolve(result)
    uploadResolvers[uuid] = nil
  }
    
  func urlSession(_ session: URLSession, task: URLSessionTask, didSendBodyData bytesSent: Int64, totalBytesSent: Int64, totalBytesExpectedToSend: Int64)
  {
    guard let uuid = session.configuration.identifier else {return}
    self.sendEvent(withName: "VideoCompressorProgress", body: ["uuid": uuid, "data": ["written": totalBytesSent, "total": totalBytesExpectedToSend]])
  }

  override func supportedEvents() -> [String]! {
    return ["videoCompressProgress", "VideoCompressorProgress", "backgroundTaskExpired"]
  }

  override func stopObserving() -> Void {
    hasListener = false
  }

  override func startObserving() -> Void {
    hasListener = true
  }
  
  
  func compressVideo(url: URL, bitRate: Float?, onProgress: @escaping (Float) -> Void,  onCompletion: @escaping (URL) -> Void, onFailure: @escaping (Error) -> Void){
    let tmpURL = URL(fileURLWithPath: NSTemporaryDirectory(), isDirectory: true)
      .appendingPathComponent(ProcessInfo().globallyUniqueString)
      .appendingPathExtension("mp4")
    let asset = AVAsset(url: url)
    guard asset.tracks.count >= 1 else {
      let error = CompressionError(message: "Invalid video URL, no track found")
      onFailure(error)
      return
    }
    let track = asset.tracks[0]
    let exporter = NextLevelSessionExporter(withAsset: asset)
    exporter.outputURL = tmpURL
    exporter.outputFileType = AVFileType.mp4
    
    let videoSize = track.naturalSize.applying(track.preferredTransform);
    var width = Float(abs(videoSize.width))
    var height = Float(abs(videoSize.height))
    let isPortrait = height > width
    let maxSize = Float(1920);
    if(isPortrait && height > maxSize){
      width = (1920/height)*width
      height = 1920
    }else if(width > maxSize){
      height = (1920/width)*height
      width = 1920
    }

    let videoBitRate = bitRate ?? height*width*1.5

    let compressionDict: [String: Any] = [
      AVVideoAverageBitRateKey: videoBitRate,
      AVVideoProfileLevelKey: AVVideoProfileLevelH264HighAutoLevel,
    ]
    exporter.optimizeForNetworkUse = true;
    exporter.videoOutputConfiguration = [
      AVVideoCodecKey: AVVideoCodecType.h264,
      AVVideoWidthKey:  width,
      AVVideoHeightKey:  height,
      AVVideoScalingModeKey: AVVideoScalingModeResizeAspectFill,
      AVVideoCompressionPropertiesKey: compressionDict
    ]
    exporter.audioOutputConfiguration = [
      AVFormatIDKey: kAudioFormatMPEG4AAC,
      AVEncoderBitRateKey: NSNumber(integerLiteral: 128000),
      AVNumberOfChannelsKey: NSNumber(integerLiteral: 2),
      AVSampleRateKey: NSNumber(value: Float(44100))
    ]
    

    exporter.export(progressHandler: { (progress) in
      onProgress(progress)
    }, completionHandler: { result in
      switch result {
      case .success(let status):
        switch status {
        case .completed:
          onCompletion(exporter.outputURL!)
          break
        default:
          let error = CompressionError(message: "Compression didn't complete")
          onFailure(error)
          break
        }
        break
      case .failure(let error):
        onFailure(error)
        break
      }
    })
  }
  
}
