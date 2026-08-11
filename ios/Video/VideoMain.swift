import Foundation
import AVFoundation
import Photos
import MobileCoreServices
import UIKit

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

        // Cap at 60 fps. 30 fps cap silently halved 60 fps recordings and
        // produced visibly choppy output.
        return min(max(nominalFrameRate, 1), 60)
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
        // WhatsApp-style bitrate envelope. The previous floors/ceilings were
        // ~2-3x larger and produced "compressed" outputs that were still
        // 20-40 MB for short clips. These bands target ~1.5 Mbps at 720p,
        // matching WhatsApp's typical output size while keeping visual
        // quality acceptable for chat playback. Must stay in sync with
        // VideoCompressionProfile.kt on Android.
        let targetLongSide = max(targetWidth, targetHeight)
        let floor: Int
        let ceiling: Int

        switch targetLongSide {
        case 1920...:
            floor = 2_000_000
            ceiling = 3_500_000
        case 1280...1919:
            floor = 1_200_000
            ceiling = 2_000_000
        case 960...1279:
            floor = 900_000
            ceiling = 1_500_000
        case 720...959:
            floor = 700_000
            ceiling = 1_200_000
        default:
            floor = 500_000
            ceiling = 900_000
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
        let bitrate = (requestedBitrate != nil && requestedBitrate! > 0) ? requestedBitrate! : estimateBitrate(
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
        let stripAudio=options["stripAudio"] as? Bool ?? false

        let asset = AVAsset(url: url)
        guard let track = getVideoTrack(asset: asset) else {
          let error = CompressionError(message: "Invalid video URL, no track found")
          onFailure(error)
          return
        }
        let profile = createCompressionProfile(track: track, maxSize: CGFloat(maxSize), requestedBitrate: nil)

        exportVideoHelper(url: url, asset: asset, bitRate: profile.bitrate, frameRate: profile.frameRate, resultWidth: profile.width, resultHeight: profile.height,uuid: uuid,progressDivider: progressDivider, stripAudio: stripAudio) { progress in
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
        let stripAudio=options["stripAudio"] as? Bool ?? false
        let asset = AVAsset(url: url)
        guard let track = getVideoTrack(asset: asset) else {
          let error = CompressionError(message: "Invalid video URL, no track found")
          onFailure(error)
          return
        }
        let maxSize = (options["maxSize"] as? NSNumber)?.floatValue ?? Float(1920)
        let profile = createCompressionProfile(track: track, maxSize: CGFloat(maxSize), requestedBitrate: bitRate)

        exportVideoHelper(url: url, asset: asset, bitRate: profile.bitrate, frameRate: profile.frameRate, resultWidth: profile.width, resultHeight: profile.height,uuid: uuid,progressDivider: progressDivider, stripAudio: stripAudio) { progress in
            onProgress(progress)
        } onCompletion: { outputURL in
            onCompletion(outputURL)
        } onFailure: { error in
            onFailure(error)
        }
      }

    func exportVideoHelper(url: URL,asset: AVAsset, bitRate: Int, frameRate: Int, resultWidth:Int,resultHeight:Int,uuid:String,progressDivider: Int, stripAudio: Bool = false, onProgress: @escaping (Float) -> Void,  onCompletion: @escaping (URL) -> Void, onFailure: @escaping (Error) -> Void){
        var currentVideoCompression:Int=0

        var tmpURL = URL(fileURLWithPath: NSTemporaryDirectory(), isDirectory: true)
          .appendingPathComponent(ProcessInfo().globallyUniqueString)
          .appendingPathExtension("mp4")
        tmpURL = URL(string: Utils.makeValidUri(filePath: tmpURL.absoluteString))!

        let exporter = NextLevelSessionExporter(withAsset: asset)
        exporter.outputURL = tmpURL
        exporter.outputFileType = AVFileType.mp4

        // NOTE: Do not add AVVideoExpectedSourceFrameRateKey or
        // AVVideoAverageNonDroppableFrameRateKey here. They are not documented
        // H.264 (avc1) compression properties on iOS and, while AVFoundation's
        // `canApply(...)` check still returns true, the iOS encoder silently
        // drops the video track, producing an audio-only MP4 that still reports
        // success. See issue #400.
        let compressionDict: [String: Any] = [
          AVVideoAverageBitRateKey: bitRate,
          AVVideoProfileLevelKey: AVVideoProfileLevelH264HighAutoLevel,
        ]
        exporter.optimizeForNetworkUse = true;
        exporter.videoOutputConfiguration = [
          AVVideoCodecKey: AVVideoCodecType.h264,
          AVVideoWidthKey:  resultWidth,
          AVVideoHeightKey:  resultHeight,
          AVVideoScalingModeKey: AVVideoScalingModeResizeAspect,
          AVVideoCompressionPropertiesKey: compressionDict
        ]
        exporter.stripAudio = stripAudio
        if !stripAudio {
            exporter.audioOutputConfiguration = [
              AVFormatIDKey: kAudioFormatMPEG4AAC,
              AVEncoderBitRateKey: NSNumber(integerLiteral: 128000),
              AVNumberOfChannelsKey: NSNumber(integerLiteral: 2),
              AVSampleRateKey: NSNumber(value: Float(44100))
            ]
        }

        // Preserve source metadata (location, creation date, etc.) by forwarding
        // every available metadata format to the writer.
        var preservedMetadata: [AVMetadataItem] = asset.metadata
        for format in asset.availableMetadataFormats {
            preservedMetadata.append(contentsOf: asset.metadata(forFormat: format))
        }
        exporter.metadata = preservedMetadata

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
            switch result {
            case .success:
              if let outputURL = exporter.outputURL {
                // Guard against the iOS encoder silently dropping the video track
                // and producing an audio-only file that still reports success.
                // See issue #400.
                let outputAsset = AVAsset(url: outputURL)
                if outputAsset.tracks(withMediaType: AVMediaType.video).isEmpty {
                  try? FileManager.default.removeItem(at: outputURL)
                  onFailure(CompressionError(message: "Compression produced a file with no video track"))
                } else {
                  onCompletion(outputURL)
                }
              } else {
                onFailure(CompressionError(message: "Compression succeeded but output URL is unavailable"))
              }
              break
            case .failure(let error):
              if let nexterError = error as? NextLevelSessionExporterError, case .cancelled = nexterError {
                onFailure(CompressionError(message: "Compression has cancelled"))
              } else {
                onFailure(error)
              }
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
        VideoCompressor.getAbsoluteVideoPath(filePath, options: [:]) { absoluteVideoPath in
            guard absoluteVideoPath.hasPrefix("file://"), let url = URL(string: absoluteVideoPath) else {
                reject("INVALID_PATH", "Video path must start with file://", nil)
                return
            }

            let path = url.path
            let fileManager = FileManager.default
            var isDir: ObjCBool = false

            if !fileManager.fileExists(atPath: path, isDirectory: &isDir) || isDir.boolValue {
                let err = NSError(domain: "file not found", code: -15, userInfo: nil)
                reject(String(err.code), err.localizedDescription, err)
                return
            }

            let attrs = try? fileManager.attributesOfItem(atPath: path)
            guard let fileSize = attrs?[FileAttributeKey.size] as? UInt64 else {
                let err = NSError(domain: "file size unavailable", code: -16, userInfo: nil)
                reject(String(err.code), err.localizedDescription, err)
                return
            }

            var result: [String: Any] = [:]
            let assetOptions: [String: Any] = [AVURLAssetPreferPreciseDurationAndTimingKey: true]
            let asset = AVURLAsset(url: URL(fileURLWithPath: path), options: assetOptions)

            // The asset's common metadata is merged first so the normalized keys written below win
            // if the two ever collide (they do for `location`).
            var commonMetadata: [AVMetadataItem] = []
            for key in self.metadatas {
                let items = AVMetadataItem.metadataItems(from: asset.commonMetadata, withKey: key, keySpace: AVMetadataKeySpace.common)
                commonMetadata.append(contentsOf: items)
            }
            for item in commonMetadata {
                if let commonKey = item.commonKey, let value = item.value {
                    result[commonKey.rawValue] = value
                }
            }

            result["extension"] = (path as NSString).pathExtension
            result["fileName"] = (path as NSString).lastPathComponent
            result["size"] = fileSize
            let time = asset.duration
            result["duration"] = time.timescale == 0 ? 0 : Double(time.value) / Double(time.timescale)

            let videoTracks = asset.tracks(withMediaType: .video)
            let audioTracks = asset.tracks(withMediaType: .audio)
            result["hasVideo"] = !videoTracks.isEmpty
            result["hasAudio"] = !audioTracks.isEmpty
            result["trackCount"] = asset.tracks.count

            // Defaults for an asset with no video track — it is a valid file, just not a video, so
            // it resolves with zeroed dimensions rather than hanging or rejecting.
            result["width"] = 0
            result["height"] = 0
            result["displayWidth"] = 0
            result["displayHeight"] = 0
            result["rotation"] = 0
            result["isHDR"] = false
            result["colorTransfer"] = "sdr"

            if let videoTrack = videoTracks.first {
                let size = videoTrack.naturalSize
                let transform = videoTrack.preferredTransform
                let displaySize = size.applying(transform)
                result["width"] = size.width
                result["height"] = size.height
                result["displayWidth"] = abs(displaySize.width)
                result["displayHeight"] = abs(displaySize.height)
                result["rotation"] = VideoCompressor.normalizedRotation(from: transform)
                result["bitrate"] = Int(videoTrack.estimatedDataRate)
                if videoTrack.nominalFrameRate > 0 {
                    result["frameRate"] = videoTrack.nominalFrameRate
                }
                if let description = videoTrack.formatDescriptions.first {
                    let formatDescription = description as! CMFormatDescription
                    result["codec"] = VideoCompressor.normalizedCodec(from: CMFormatDescriptionGetMediaSubType(formatDescription))
                    let transfer = VideoCompressor.colorTransferName(from: formatDescription)
                    result["colorTransfer"] = transfer
                    result["isHDR"] = transfer == "hlg" || transfer == "pq"
                }
            }

            if let audioTrack = audioTracks.first {
                result["audioBitrate"] = Int(audioTrack.estimatedDataRate)
                if let description = audioTrack.formatDescriptions.first {
                    let formatDescription = description as! CMFormatDescription
                    if let basic = CMAudioFormatDescriptionGetStreamBasicDescription(formatDescription)?.pointee {
                        result["audioSampleRate"] = basic.mSampleRate
                        result["audioChannels"] = Int(basic.mChannelsPerFrame)
                    }
                }
            }

            let locationItems = AVMetadataItem.metadataItems(from: asset.commonMetadata, withKey: AVMetadataKey.commonKeyLocation, keySpace: AVMetadataKeySpace.common)
            if let location = locationItems.first?.stringValue {
                result["location"] = location
                if let coordinate = VideoCompressor.parseIso6709(location) {
                    result["latitude"] = coordinate.latitude
                    result["longitude"] = coordinate.longitude
                }
            }

            resolve(result)
        }
    }

    /// Snaps the track's preferred transform to the nearest quarter turn in `0, 90, 180, 270`.
    private static func normalizedRotation(from transform: CGAffineTransform) -> Int {
        let degrees = atan2(transform.b, transform.a) * 180 / .pi
        let quarters = Int((degrees / 90).rounded())
        return ((quarters % 4) + 4) % 4 * 90
    }

    private static func normalizedCodec(from subType: FourCharCode) -> String {
        switch subType {
        case kCMVideoCodecType_H264: return "h264"
        case kCMVideoCodecType_HEVC, kCMVideoCodecType_HEVCWithAlpha: return "hevc"
        case kCMVideoCodecType_MPEG4Video: return "mpeg4"
        case kCMVideoCodecType_JPEG: return "jpeg"
        default:
            // Render the remaining tags from their fourCC, e.g. 'av01' -> "av01", 'vp09' -> "vp09".
            let bytes = [24, 16, 8, 0].map { UInt8((subType >> $0) & 0xFF) }
            let tag = String(bytes: bytes, encoding: .ascii)?.trimmingCharacters(in: .whitespaces).lowercased() ?? ""
            switch tag {
            case "av01": return "av1"
            case "vp09": return "vp9"
            case "vp08": return "vp8"
            default: return tag
            }
        }
    }

    private static func colorTransferName(from formatDescription: CMFormatDescription) -> String {
        let extensions = CMFormatDescriptionGetExtensions(formatDescription) as? [String: Any]
        guard let transferFunction = extensions?[kCMFormatDescriptionExtension_TransferFunction as String] as? String else {
            return "sdr"
        }
        switch transferFunction as CFString {
        case kCMFormatDescriptionTransferFunction_ITU_R_2100_HLG: return "hlg"
        case kCMFormatDescriptionTransferFunction_SMPTE_ST_2084_PQ: return "pq"
        case kCMFormatDescriptionTransferFunction_Linear: return "linear"
        default: return "sdr"
        }
    }

    /// Parses the ISO-6709 string `AVAsset` exposes (`"+37.5090-122.2620/"`) into signed decimal
    /// degrees, so no caller has to write an ISO-6709 parser.
    private static func parseIso6709(_ location: String) -> (latitude: Double, longitude: Double)? {
        let pattern = "([+-]\\d+(?:\\.\\d+)?)([+-]\\d+(?:\\.\\d+)?)"
        guard let regex = try? NSRegularExpression(pattern: pattern),
              let match = regex.firstMatch(in: location, range: NSRange(location.startIndex..., in: location)),
              let latitudeRange = Range(match.range(at: 1), in: location),
              let longitudeRange = Range(match.range(at: 2), in: location),
              let latitude = Double(location[latitudeRange]),
              let longitude = Double(location[longitudeRange]) else {
            return nil
        }
        return (latitude, longitude)
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
