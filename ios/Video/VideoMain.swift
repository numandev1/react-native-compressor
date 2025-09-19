import Foundation
import AVFoundation
import Photos
import MobileCoreServices

struct CompressionError: Error {
  private let message: String

  var localizedDescription: String {
    return message
  }

  init(message: String) {
    self.message = message
  }
}

class VideoCompressor {
  var backgroundTaskId: UIBackgroundTaskIdentifier = .invalid;

  var compressorExports: [String: NextLevelSessionExporter] = [:]

    let metadatas: [String] = [
        "albumName",
        "artist",
        "comment",
        "copyrights",
        "creationDate",
        "date",
        "encodedby",
        "genre",
        "language",
        "location",
        "lastModifiedDate",
        "performer",
        "publisher",
        "title"
    ]

  func activateBackgroundTask(options: [String: Any], resolve:@escaping RCTPromiseResolveBlock, reject:@escaping RCTPromiseRejectBlock) -> Void {
    guard backgroundTaskId == .invalid else {
      reject("failed", "There is a background task already", nil)
      return
    }
    backgroundTaskId = UIApplication.shared.beginBackgroundTask(
      withName: "video-upload",
      expirationHandler: {
        EventEmitterHandler.emitBackgroundTaskExpired(self.backgroundTaskId)
        UIApplication.shared.endBackgroundTask(self.backgroundTaskId)
        self.backgroundTaskId = .invalid
    })
    resolve(backgroundTaskId)
  }

  func deactivateBackgroundTask(options: [String: Any], resolve:@escaping RCTPromiseResolveBlock, reject:@escaping RCTPromiseRejectBlock) -> Void {
    guard backgroundTaskId != .invalid else {
      reject("failed", "There is no active background task", nil)
        return
    }
    UIApplication.shared.endBackgroundTask(backgroundTaskId)
    resolve(nil)
    backgroundTaskId = .invalid
  }

  func compress(fileUrl: String, options: [String: Any], resolve:@escaping RCTPromiseResolveBlock, reject:@escaping RCTPromiseRejectBlock) -> Void {
    let progressDivider=options["progressDivider"] as? Int ?? 0
    compressVideo(url: URL(string: fileUrl)!, options:options,
    onProgress: { progress in
        EventEmitterHandler.emitVideoCompressProgress(progress, uuid: options["uuid"] as! String)
    }, onCompletion: { newUrl in
      resolve("\(newUrl)");
    }, onFailure: { error in
      reject("failed", "Compression Failed", error)
    })
  }

    func cancelCompression(uuid: String) -> Void {
        compressorExports[uuid]?.cancelExport()
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
      let uuid:String = options["uuid"] as! String

      VideoCompressor.getAbsoluteVideoPath(url.absoluteString, options: options) { absoluteVideoPath in
        var minimumFileSizeForCompress:Double=0.0;
          let videoURL = URL(string: absoluteVideoPath)
        let fileSize=self.getfileSize(forURL: videoURL!);
          if((options["minimumFileSizeForCompress"]) != nil)
        {0
              minimumFileSizeForCompress=options["minimumFileSizeForCompress"] as! Double;
        }
        if(fileSize>minimumFileSizeForCompress)
        {
            if(options["compressionMethod"] as! String=="auto")
            {
                self.autoCompressionHelper(url: videoURL!, options:options) { progress in
                    onProgress(progress)
                } onCompletion: { outputURL in
                    MediaCache.removeCompletedImagePath(absoluteVideoPath);
                    onCompletion(outputURL)
                } onFailure: { error in
                    onFailure(error)
                }
            }
            else
            {
                self.manualCompressionHelper(url: videoURL!, options:options) { progress in
                    onProgress(progress)
                } onCompletion: { outputURL in
                    MediaCache.removeCompletedImagePath(absoluteVideoPath);
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
}


    func  makeVideoBitrate(originalHeight:Int,originalWidth:Int,originalBitrate:Int,height:Int,width:Int)->Int {
        let compressFactor:Float = 0.8
        let  minCompressFactor:Float = 0.8
        let maxBitrate:Int = 1669000
        let minValue:Float=min(Float(originalHeight)/Float(height),Float(originalWidth)/Float(width))
        var remeasuredBitrate:Int = Int(Float(originalBitrate) / minValue)
        remeasuredBitrate = Int(Float(remeasuredBitrate)*compressFactor)
        let minBitrate:Int = Int(Float(self.getVideoBitrateWithFactor(f: minCompressFactor)) / (1280 * 720 / Float(width * height)))
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
        let uuid:String = options["uuid"] as! String
        let progressDivider=options["progressDivider"] as? Int ?? 0

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
        let resultWidth:Float = round(actualWidth * min(scale, 1) / 2) * 2;
        let resultHeight:Float = round(actualHeight * min(scale, 1) / 2) * 2;

        let videoBitRate:Int = self.makeVideoBitrate(
            originalHeight: Int(actualHeight), originalWidth: Int(actualWidth),
            originalBitrate: Int(bitrate),
            height: Int(resultHeight), width: Int(resultWidth)
            );

        exportVideoHelper(url: url, asset: asset, bitRate: videoBitRate, resultWidth: resultWidth, resultHeight: resultHeight,uuid: uuid,progressDivider: progressDivider) { progress in
            onProgress(progress)
        } onCompletion: { outputURL in
            onCompletion(outputURL)
        } onFailure: { error in
            onFailure(error)
        }
      }

    func manualCompressionHelper(url: URL, options: [String: Any], onProgress: @escaping (Float) -> Void,  onCompletion: @escaping (URL) -> Void, onFailure: @escaping (Error) -> Void){
        let uuid:String = options["uuid"] as! String
        var bitRate = (options["bitrate"] as? NSNumber)?.floatValue;
        let progressDivider=options["progressDivider"] as? Int ?? 0
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
        let maxSize = (options["maxSize"] as! Float?) ?? Float(1920);
        if(isPortrait && height > maxSize){
          width = (maxSize/height)*width
          height = maxSize
        }else if(width > maxSize){
          height = (maxSize/width)*height
          width = maxSize
        }
        else
        {
            bitRate=bitRate ?? Float(abs(track.estimatedDataRate))*0.8
        }

        let videoBitRate = bitRate ?? height*width*1.5

        exportVideoHelper(url: url, asset: asset, bitRate: Int(videoBitRate), resultWidth: width, resultHeight: height,uuid: uuid,progressDivider: progressDivider) { progress in
            onProgress(progress)
        } onCompletion: { outputURL in
            onCompletion(outputURL)
        } onFailure: { error in
            onFailure(error)
        }
      }

    func exportVideoHelper(url: URL,asset: AVAsset, bitRate: Int,resultWidth:Float,resultHeight:Float,uuid:String,progressDivider: Int, onProgress: @escaping (Float) -> Void,  onCompletion: @escaping (URL) -> Void, onFailure: @escaping (Error) -> Void){
        var currentVideoCompression:Int=0

        var tmpURL = URL(fileURLWithPath: NSTemporaryDirectory(), isDirectory: true)
          .appendingPathComponent(ProcessInfo().globallyUniqueString)
          .appendingPathExtension("mp4")
        tmpURL = URL(string: Utils.makeValidUri(filePath: tmpURL.absoluteString))!

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

        compressorExports[uuid] = exporter
        exporter.export(progressHandler: { (progress) in
            let roundProgress:Int=Int((progress*100).rounded());
            if(progressDivider==0||(roundProgress%progressDivider==0&&roundProgress>currentVideoCompression))
            {
            currentVideoCompression=roundProgress
            onProgress(progress)
            }
        }, completionHandler: { result in
            currentVideoCompression=0;
            switch exporter.status {
            case .completed:
              onCompletion(exporter.outputURL!)
              break
            case .cancelled:
                let error = CompressionError(message: "Compression has canncelled")
                onFailure(error)
                break
            default:
                onCompletion(url)
              break
          }
        })
    }

    func getVideoTrack(asset: AVAsset) -> AVAssetTrack {
        let tracks = asset.tracks(withMediaType: AVMediaType.video)
        return tracks[0];
        }



    func getVideoMetaData(_ filePath: String, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        do {
            VideoCompressor.getAbsoluteVideoPath(filePath, options: [:]) { absoluteImagePath in
                if absoluteImagePath.hasPrefix("file://") {

                    let absoluteImagePath = URL(string: absoluteImagePath)!.path
                    let fileManager = FileManager.default
                    var isDir: ObjCBool = false

                    if !fileManager.fileExists(atPath: absoluteImagePath, isDirectory: &isDir) || isDir.boolValue {
                        let err = NSError(domain: "file not found", code: -15, userInfo: nil)
                        reject(String(err.code), err.localizedDescription, err)
                        return
                    }

                    let attrs = try? fileManager.attributesOfItem(atPath: absoluteImagePath)
                    if let fileSize = attrs?[FileAttributeKey.size] as? UInt64 {
                        let fileSizeString = fileSize

                        var result: [String: Any] = [:]
                        let assetOptions: [String: Any] = [AVURLAssetPreferPreciseDurationAndTimingKey: true]
                        let asset = AVURLAsset(url: URL(fileURLWithPath: absoluteImagePath), options: assetOptions)
                        if let avAsset = asset.tracks(withMediaType: .video).first {
                            let size = avAsset.naturalSize
                            let _extension = (absoluteImagePath as NSString).pathExtension
                            let time = asset.duration
                            let seconds = Double(time.value) / Double(time.timescale)

                            result["width"] = size.width
                            result["height"] = size.height
                            result["extension"] = _extension
                            result["size"] = fileSizeString
                            result["duration"] = seconds

                            var commonMetadata: [AVMetadataItem] = []
                            for key in self.metadatas {
                                let items = AVMetadataItem.metadataItems(from: asset.commonMetadata, withKey: key, keySpace: AVMetadataKeySpace.common)
                                commonMetadata.append(contentsOf: items)
                            }

                            for item in commonMetadata {
                                if let value = item.value {
                                    result[item.commonKey!.rawValue] = value
                                }
                            }

                            resolve(result)
                        }
                    }
                }
            }
        } catch {
            reject(error.localizedDescription, error.localizedDescription, nil)
        }
    }

    static func getAbsoluteVideoPath(_ videoPath: String, options: [String: Any], completionHandler: @escaping (String) -> Void) {
        if videoPath.hasPrefix("http://") || videoPath.hasPrefix("https://") {
            let uuid=options["uuid"] as? String ?? ""
            let progressDivider=options["progressDivider"] as? Int ?? 0
            Downloader.downloadFileAndSaveToCache(videoPath, uuid: uuid,progressDivider:progressDivider) { downloadedPath in
                completionHandler(downloadedPath)
            }
            return
        } else if !videoPath.contains("ph://") {
            completionHandler(Utils.slashifyFilePath(path: videoPath)!)
            return
        }
        let assetId = videoPath.replacingOccurrences(of: "ph://", with: "")
        let outputFileType = AVFileType.mp4
        let pressetType = AVAssetExportPresetPassthrough

        if assetId.isEmpty {
            let error = NSError(domain: "RNGalleryManager", code: -91, userInfo: nil)
            let exception = NSException(name: NSExceptionName(rawValue: "RNGalleryManager"), reason: "Empty asset ID.", userInfo: nil)
            exception.raise()
            return
        }

        let localIds = [assetId]
        guard let videoAsset = PHAsset.fetchAssets(withLocalIdentifiers: localIds, options: nil).firstObject else {
            return
        }

        let mimeType = UTTypeCopyPreferredTagWithClass(outputFileType as CFString, kUTTagClassMIMEType)?.takeRetainedValue() as String? ?? ""
        let uti = UTTypeCreatePreferredIdentifierForTag(kUTTagClassMIMEType, mimeType as CFString, nil)?.takeRetainedValue() as String? ?? ""
        let extensionValue = UTTypeCopyPreferredTagWithClass(uti as CFString, kUTTagClassFilenameExtension)?.takeRetainedValue() as String? ?? ""
        let path = Utils.generateCacheFilePath(extensionValue)
        let outputUrl = URL(string: "file://\(path)")!

        let videoRequestOptions = PHVideoRequestOptions()
        videoRequestOptions.isNetworkAccessAllowed = true
        videoRequestOptions.deliveryMode = .highQualityFormat

        PHImageManager.default().requestExportSession(forVideo: videoAsset, options: videoRequestOptions, exportPreset: pressetType) { exportSession, _ in
            guard let exportSession = exportSession else {
                let error = NSError(domain: "RNGalleryManager", code: -91, userInfo: nil)
                let exception = NSException(name: NSExceptionName(rawValue: "RNGalleryManager"), reason: "Export session is nil.", userInfo: nil)
                exception.raise()
                return
            }

            exportSession.shouldOptimizeForNetworkUse = true
            exportSession.outputFileType = outputFileType
            exportSession.outputURL = outputUrl

            exportSession.exportAsynchronously {
                switch exportSession.status {
                case .failed:
                    let error = exportSession.error ?? NSError(domain: "RNGalleryManager", code: -91, userInfo: nil)
                    let codeWithDomain = "E\(error.localizedDescription)\(error)"
                    let exception = NSException(name: NSExceptionName(rawValue: codeWithDomain), reason: "Video export failed.", userInfo: nil)
                    exception.raise()
                case .cancelled:
                    let error = NSError(domain: "RNGalleryManager", code: -91, userInfo: nil)
                    let exception = NSException(name: NSExceptionName(rawValue: "RNGalleryManager"), reason: "Video export cancelled.", userInfo: nil)
                    exception.raise()
                case .completed:
                    completionHandler(outputUrl.absoluteString)
                default:
                    let error = NSError(domain: "RNGalleryManager", code: -91, userInfo: nil)
                    let exception = NSException(name: NSExceptionName(rawValue: "RNGalleryManager"), reason: "Unknown status.", userInfo: nil)
                    exception.raise()
                }
            }
        }
    }

    }
