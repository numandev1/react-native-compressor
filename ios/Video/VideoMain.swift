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
        {
              minimumFileSizeForCompress=(options["minimumFileSizeForCompress"] as? NSNumber)?.doubleValue ?? 0;
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


    struct CompressionProfile {
        let width: Int
        let height: Int
        let bitrate: Int
        let frameRate: Int
    }

    func normalizeDimension(_ value: CGFloat) -> Int {
        let rounded = max(Int(value.rounded()), 2)
        return rounded % 2 == 0 ? rounded : rounded - 1
    }

    func normalizeFrameRate(for track: AVAssetTrack) -> Int {
        let nominalFrameRate = Int(track.nominalFrameRate.rounded())
        if nominalFrameRate <= 0 {
            return 30
        }

        return min(max(nominalFrameRate, 1), 30)
    }

    func scaledDimensions(width: CGFloat, height: CGFloat, maxSize: CGFloat) -> (width: Int, height: Int) {
        let safeWidth = normalizeDimension(width)
        let safeHeight = normalizeDimension(height)
        let longSide = max(safeWidth, safeHeight)
        let safeMaxSize = max(Int(maxSize.rounded()), 2)

        guard longSide > safeMaxSize else {
            return (safeWidth, safeHeight)
        }

        let scale = CGFloat(safeMaxSize) / CGFloat(longSide)
        return (
            normalizeDimension(CGFloat(safeWidth) * scale),
            normalizeDimension(CGFloat(safeHeight) * scale)
        )
    }

    func estimateBitrate(
        originalWidth: Int,
        originalHeight: Int,
        originalBitrate: Int,
        originalFrameRate: Int,
        targetWidth: Int,
        targetHeight: Int,
        targetFrameRate: Int
    ) -> Int {
        let targetLongSide = max(targetWidth, targetHeight)
        let floor: Int
        let ceiling: Int

        switch targetLongSide {
        case 1920...:
            floor = 4_000_000
            ceiling = 8_000_000
        case 1280...1919:
            floor = 2_200_000
            ceiling = 5_000_000
        case 960...1279:
            floor = 1_600_000
            ceiling = 3_500_000
        case 720...959:
            floor = 1_200_000
            ceiling = 2_500_000
        default:
            floor = 850_000
            ceiling = 1_500_000
        }

        guard originalBitrate > 0 else {
            return floor
        }

        let originalPixels = max(originalWidth * originalHeight, 1)
        let targetPixels = max(targetWidth * targetHeight, 1)
        let pixelRatio = Double(targetPixels) / Double(originalPixels)
        let safeOriginalFrameRate = max(originalFrameRate, 1)
        let frameRateRatio = Double(targetFrameRate) / Double(safeOriginalFrameRate)
        let scaledBitrate = Int((Double(originalBitrate) * pixelRatio * max(frameRateRatio, 0.85)).rounded())
        let sourceCap = max(Int((Double(originalBitrate) * 0.95).rounded()), floor)
        return min(max(scaledBitrate, floor), min(ceiling, sourceCap))
    }

    func createCompressionProfile(track: AVAssetTrack, maxSize: CGFloat, requestedBitrate: Int?) -> CompressionProfile {
        let videoSize = track.naturalSize.applying(track.preferredTransform)
        let actualWidth = abs(videoSize.width)
        let actualHeight = abs(videoSize.height)
        let dimensions = scaledDimensions(width: actualWidth, height: actualHeight, maxSize: maxSize)
        let frameRate = normalizeFrameRate(for: track)
        let sourceFrameRate = Int(track.nominalFrameRate.rounded())
        let bitrate = requestedBitrate ?? estimateBitrate(
            originalWidth: normalizeDimension(actualWidth),
            originalHeight: normalizeDimension(actualHeight),
            originalBitrate: Int(abs(track.estimatedDataRate.rounded())),
            originalFrameRate: sourceFrameRate,
            targetWidth: dimensions.width,
            targetHeight: dimensions.height,
            targetFrameRate: frameRate
        )

        return CompressionProfile(
            width: dimensions.width,
            height: dimensions.height,
            bitrate: max(bitrate, 1),
            frameRate: frameRate
        )
    }

    func autoCompressionHelper(url: URL, options: [String: Any], onProgress: @escaping (Float) -> Void,  onCompletion: @escaping (URL) -> Void, onFailure: @escaping (Error) -> Void){
        let maxSize = (options["maxSize"] as? NSNumber)?.floatValue ?? 640
        let uuid:String = options["uuid"] as! String
        let progressDivider=options["progressDivider"] as? Int ?? 0

        let asset = AVAsset(url: url)
        guard let track = getVideoTrack(asset: asset) else {
          let error = CompressionError(message: "Invalid video URL, no track found")
          onFailure(error)
          return
        }
        let profile = createCompressionProfile(track: track, maxSize: CGFloat(maxSize), requestedBitrate: nil)

        exportVideoHelper(url: url, asset: asset, bitRate: profile.bitrate, frameRate: profile.frameRate, resultWidth: profile.width, resultHeight: profile.height,uuid: uuid,progressDivider: progressDivider) { progress in
            onProgress(progress)
        } onCompletion: { outputURL in
            onCompletion(outputURL)
        } onFailure: { error in
            onFailure(error)
        }
      }

    func manualCompressionHelper(url: URL, options: [String: Any], onProgress: @escaping (Float) -> Void,  onCompletion: @escaping (URL) -> Void, onFailure: @escaping (Error) -> Void){
        let uuid:String = options["uuid"] as! String
        let bitRate = (options["bitrate"] as? NSNumber)?.intValue
        let progressDivider=options["progressDivider"] as? Int ?? 0
        let asset = AVAsset(url: url)
        guard let track = getVideoTrack(asset: asset) else {
          let error = CompressionError(message: "Invalid video URL, no track found")
          onFailure(error)
          return
        }
        let maxSize = (options["maxSize"] as? NSNumber)?.floatValue ?? Float(1920)
        let profile = createCompressionProfile(track: track, maxSize: CGFloat(maxSize), requestedBitrate: bitRate)

        exportVideoHelper(url: url, asset: asset, bitRate: profile.bitrate, frameRate: profile.frameRate, resultWidth: profile.width, resultHeight: profile.height,uuid: uuid,progressDivider: progressDivider) { progress in
            onProgress(progress)
        } onCompletion: { outputURL in
            onCompletion(outputURL)
        } onFailure: { error in
            onFailure(error)
        }
      }

    func exportVideoHelper(url: URL,asset: AVAsset, bitRate: Int, frameRate: Int, resultWidth:Int,resultHeight:Int,uuid:String,progressDivider: Int, onProgress: @escaping (Float) -> Void,  onCompletion: @escaping (URL) -> Void, onFailure: @escaping (Error) -> Void){
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
          AVVideoExpectedSourceFrameRateKey: frameRate,
          AVVideoAverageNonDroppableFrameRateKey: frameRate,
        ]
        exporter.optimizeForNetworkUse = true;
        exporter.videoOutputConfiguration = [
          AVVideoCodecKey: AVVideoCodecType.h264,
          AVVideoWidthKey:  resultWidth,
          AVVideoHeightKey:  resultHeight,
          AVVideoScalingModeKey: AVVideoScalingModeResizeAspect,
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
            self.compressorExports[uuid] = nil
            switch exporter.status {
            case .completed:
              onCompletion(exporter.outputURL!)
              break
            case .cancelled:
                let error = CompressionError(message: "Compression has canncelled")
                onFailure(error)
                break
            default:
                onFailure(exporter.error ?? CompressionError(message: "Compression failed"))
              break
          }
        })
    }

    func getVideoTrack(asset: AVAsset) -> AVAssetTrack? {
        let tracks = asset.tracks(withMediaType: AVMediaType.video)
        guard tracks.count >= 1 else {
            return nil
        }
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
