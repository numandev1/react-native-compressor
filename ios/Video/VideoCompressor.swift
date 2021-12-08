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
    let videoCompressionThreshold:Int=7
    var videoCompressionCounter:Int=0

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
    compressVideo(url: URL(string: fileUrl)!, options:options,
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
    
func makeValidUri(filePath: String) -> String {
    let fileWithUrl = URL(fileURLWithPath: filePath)
    let absoluteUrl = fileWithUrl.deletingLastPathComponent()
    let fileUrl = "file://\(absoluteUrl.path)/\(fileWithUrl.lastPathComponent)"
    return fileUrl;
}

  @objc(upload:withOptions:withResolver:withRejecter:)
  func upload(filePath: String, options: [String: Any], resolve:@escaping RCTPromiseResolveBlock, reject:@escaping RCTPromiseRejectBlock) -> Void {
    let fileUrl = makeValidUri(filePath: filePath)
    
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
    
    func getfileSize(forURL url: Any) -> Double {
        var fileURL: URL?
        var fileSize: Double = 0.0
        if (url is URL) || (url is String)
        {
            if (url is URL) {
                fileURL = url as? URL
            }
            else {
                fileURL = URL(fileURLWithPath: url as! String)
            }
            var fileSizeValue = 0.0
            try? fileSizeValue = (fileURL?.resourceValues(forKeys: [URLResourceKey.fileSizeKey]).allValues.first?.value as! Double?)!
            if fileSizeValue > 0.0 {
                fileSize = (Double(fileSizeValue) / (1024 * 1024))
            }
        }
        return fileSize
    }
  
  
  func compressVideo(url: URL, options: [String: Any], onProgress: @escaping (Float) -> Void,  onCompletion: @escaping (URL) -> Void, onFailure: @escaping (Error) -> Void){
      var minimumFileSizeForCompress:Double=16.0;
    let fileSize=self.getfileSize(forURL: url);
      if((options["minimumFileSizeForCompress"]) != nil)
    {
          minimumFileSizeForCompress=options["minimumFileSizeForCompress"] as! Double;
    }
    if(fileSize>minimumFileSizeForCompress)
    {
        if(options["compressionMethod"] as! String=="auto")
        {
            autoCompressionHelper(url: url, options:options) { progress in
                onProgress(progress)
            } onCompletion: { outputURL in
                onCompletion(outputURL)
            } onFailure: { error in
                onFailure(error)
            }
        }
        else
        {
            manualCompressionHelper(url: url, bitRate: options["bitrate"] as! Float?) { progress in
                onProgress(progress)
            } onCompletion: { outputURL in
                onCompletion(outputURL)
            } onFailure: { error in
                onFailure(error)
            }
        }
        
        

    }
    else
    {
        onCompletion(url)
    }
    
    
  
}


    func  makeVideoBitrate(originalHeight:Int,originalWidth:Int,originalBitrate:Int,height:Int,width:Int)->Int {
        let compressFactor:Float = 0.8
        let  minCompressFactor:Float = 0.8
        let maxBitrate:Int = 1669000
        let minValue:Float=min(Float(originalHeight)/Float(height),Float(originalWidth)/Float(width))
        var remeasuredBitrate:Int = Int(Float(originalBitrate) / minValue)
        remeasuredBitrate = remeasuredBitrate*Int(compressFactor)
        let minBitrate:Int = self.getVideoBitrateWithFactor(f: minCompressFactor) / (1280 * 720 / (width * height))
        if (originalBitrate < minBitrate) {
          return remeasuredBitrate;
        }
        if (remeasuredBitrate > maxBitrate) {
          return maxBitrate;
        }
        return max(remeasuredBitrate, minBitrate);
      }
    func getVideoBitrateWithFactor(f:Float)->Int {
        return Int(f * 2000 * 1000 * 1.13);
      }
    
    func autoCompressionHelper(url: URL, options: [String: Any], onProgress: @escaping (Float) -> Void,  onCompletion: @escaping (URL) -> Void, onFailure: @escaping (Error) -> Void){
        let maxSize:Float = options["maxSize"] as! Float;
    
        let asset = AVAsset(url: url)
        guard asset.tracks.count >= 1 else {
          let error = CompressionError(message: "Invalid video URL, no track found")
          onFailure(error)
          return
        }
        let track = getVideoTrack(asset: asset);
        
        let videoSize = track.naturalSize.applying(track.preferredTransform);
        let actualWidth = Float(abs(videoSize.width))
        let actualHeight = Float(abs(videoSize.height))

        let bitrate=Float(abs(track.estimatedDataRate));
        let scale:Float = actualWidth > actualHeight ? (Float(maxSize) / actualWidth) : (Float(maxSize) / actualHeight);
        let resultWidth:Float = round(actualWidth * scale / 2) * 2;
        let resultHeight:Float = round(actualHeight * scale / 2) * 2;

        let videoBitRate:Int = self.makeVideoBitrate(
            originalHeight: Int(actualHeight), originalWidth: Int(actualWidth),
            originalBitrate: Int(bitrate),
            height: Int(resultHeight), width: Int(resultWidth)
            );

        exportVideoHelper(url: url, asset: asset, bitRate: videoBitRate, resultWidth: resultWidth, resultHeight: resultHeight) { progress in
            onProgress(progress)
        } onCompletion: { outputURL in
            onCompletion(outputURL)
        } onFailure: { error in
            onFailure(error)
        }
      }

    func manualCompressionHelper(url: URL, bitRate: Float?, onProgress: @escaping (Float) -> Void,  onCompletion: @escaping (URL) -> Void, onFailure: @escaping (Error) -> Void){
        
        var _bitRate=bitRate;
        let asset = AVAsset(url: url)
        guard asset.tracks.count >= 1 else {
          let error = CompressionError(message: "Invalid video URL, no track found")
          onFailure(error)
          return
        }
        let track = getVideoTrack(asset: asset);
        
        let videoSize = track.naturalSize.applying(track.preferredTransform);
        var width = Float(abs(videoSize.width))
        var height = Float(abs(videoSize.height))
        let isPortrait = height > width
        let maxSize = Float(1920);
        if(isPortrait && height > maxSize){
          width = (maxSize/height)*width
          height = maxSize
        }else if(width > maxSize){
          height = (maxSize/width)*height
          width = maxSize
        }
        else
        {
            _bitRate=bitRate ?? Float(abs(track.estimatedDataRate))*0.8
        }

        let videoBitRate = _bitRate ?? height*width*1.5

        exportVideoHelper(url: url, asset: asset, bitRate: Int(videoBitRate), resultWidth: width, resultHeight: height) { progress in
            onProgress(progress)
        } onCompletion: { outputURL in
            onCompletion(outputURL)
        } onFailure: { error in
            onFailure(error)
        }
      }
    
    func exportVideoHelper(url: URL,asset: AVAsset, bitRate: Int,resultWidth:Float,resultHeight:Float, onProgress: @escaping (Float) -> Void,  onCompletion: @escaping (URL) -> Void, onFailure: @escaping (Error) -> Void){
        var tmpURL = URL(fileURLWithPath: NSTemporaryDirectory(), isDirectory: true)
          .appendingPathComponent(ProcessInfo().globallyUniqueString)
          .appendingPathExtension("mp4")
        tmpURL = URL(string:makeValidUri(filePath: tmpURL.absoluteString))!
        
        let exporter = NextLevelSessionExporter(withAsset: asset)
        exporter.outputURL = tmpURL
        exporter.outputFileType = AVFileType.mp4
        
        let compressionDict: [String: Any] = [
          AVVideoAverageBitRateKey: bitRate,
          AVVideoProfileLevelKey: AVVideoProfileLevelH264HighAutoLevel,
        ]
        exporter.optimizeForNetworkUse = true;
        exporter.videoOutputConfiguration = [
          AVVideoCodecKey: AVVideoCodecType.h264,
          AVVideoWidthKey:  resultWidth,
          AVVideoHeightKey:  resultHeight,
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
            let _progress:Float=progress*100;
            if(Int(_progress)==self.videoCompressionCounter)
            {
            self.videoCompressionCounter=Int(_progress)+self.videoCompressionThreshold
            onProgress(progress)
            }
            
        }, completionHandler: { result in
            self.videoCompressionCounter=0;
          switch result {
          case .success(let status):
            switch status {
            case .completed:
              onCompletion(exporter.outputURL!)
              break
            default:
                onCompletion(url)
              break
            }
            break
          case .failure(let error):
            onCompletion(url)
            break
          }
        })
    }
    
    func getVideoTrack(asset: AVAsset) -> AVAssetTrack {
        var videoTrackIndex: Int = 0;
        let trackLength = asset.tracks.count;
        if(trackLength==2)
        {
            if(asset.tracks[0].mediaType.rawValue=="soun")
            {
                videoTrackIndex=1;
            }
        }
        let track = asset.tracks[videoTrackIndex];
        return track;
        }
    }
