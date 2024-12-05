//  from: https://github.com/NextLevel/NextLevelSessionExporter

import Foundation
import AVFoundation
import CoreAudioTypes

// TODO: need to add a support for check if this is not defined in the coreaudio types
let kAudioFormatAPAC: UInt32 = 1634754915 // 'apac'

/// Session export errors.
public enum NextLevelSessionExporterError: Error, CustomStringConvertible {
    case setupFailure
    case readingFailure
    case writingFailure
    case cancelled
    
    public var description: String {
        get {
            switch self {
            case .setupFailure:
                return "Setup failure"
            case .readingFailure:
                return "Reading failure"
            case .writingFailure:
                return "Writing failure"
            case .cancelled:
                return "Cancelled"
            }
        }
    }
}

// MARK: - NextLevelSessionExporter

/// 🔄 NextLevelSessionExporter, export and transcode media in Swift
open class NextLevelSessionExporter: NSObject {

    /// Input asset for export, provided when initialized.
    public var asset: AVAsset?
    
    /// Enables video composition and parameters for the session.
    public var videoComposition: AVVideoComposition?
    
    /// Enables audio mixing and parameters for the session.
    public var audioMix: AVAudioMix?
    
    /// Output file location for the session.
    public var outputURL: URL?
    
    /// Output file type. UTI string defined in `AVMediaFormat.h`.
    public var outputFileType: AVFileType? = AVFileType.mp4
    
    /// Time range or limit of an export from `kCMTimeZero` to `kCMTimePositiveInfinity`
    public var timeRange: CMTimeRange
    
    /// Indicates if an export session should expect media data in real time.
    public var expectsMediaDataInRealTime: Bool = false
    
    /// Indicates if an export should be optimized for network use.
    public var optimizeForNetworkUse: Bool = false
    
    /// Metadata to be added to an export.
    public var metadata: [AVMetadataItem]?
    
    /// Video input configuration dictionary, using keys defined in `<CoreVideo/CVPixelBuffer.h>`
    public var videoInputConfiguration: [String : Any]?
    
    /// Video output configuration dictionary, using keys defined in `<AVFoundation/AVVideoSettings.h>`
    public var videoOutputConfiguration: [String : Any]?
    
    /// Audio output configuration dictionary, using keys defined in `<AVFoundation/AVAudioSettings.h>`
    public var audioOutputConfiguration: [String : Any]?
    
    /// Export session status state.
    public var status: AVAssetExportSession.Status {
        get {
            if let writer = self._writer {
                switch writer.status {
                case .writing:
                    return .exporting
                case .failed:
                    return .failed
                case .completed:
                    return .completed
                case.cancelled:
                    return .cancelled
                case .unknown:
                    fallthrough
                @unknown default:
                    break
                }
            }
            return .unknown
        }
    }
    
    /// Session exporting progress from 0 to 1.
    public var progress: Float {
        get {
            return self._progress
        }
    }
    
    // private instance vars
    
    fileprivate let InputQueueLabel = "NextLevelSessionExporterInputQueue"

    fileprivate var _writer: AVAssetWriter?
    fileprivate var _reader: AVAssetReader?
    fileprivate var _pixelBufferAdaptor: AVAssetWriterInputPixelBufferAdaptor?
    
    fileprivate var _inputQueue: DispatchQueue
    
    fileprivate var _videoOutput: AVAssetReaderVideoCompositionOutput?
    fileprivate var _audioOutput: AVAssetReaderAudioMixOutput?
    fileprivate var _videoInput: AVAssetWriterInput?
    fileprivate var _audioInput: AVAssetWriterInput?
    
    fileprivate var _progress: Float = 0
    
    fileprivate var _progressHandler: ProgressHandler?
    fileprivate var _renderHandler: RenderHandler?
    fileprivate var _completionHandler: CompletionHandler?
    
    fileprivate var _duration: TimeInterval = 0
    fileprivate var _lastSamplePresentationTime: CMTime = .invalid
    
    // MARK: - object lifecycle
    
    /// Initializes a session with an asset to export.
    ///
    /// - Parameter asset: The asset to export.
    public convenience init(withAsset asset: AVAsset) {
        self.init()
        self.asset = asset
    }
    
    public override init() {
        self._inputQueue = DispatchQueue(label: InputQueueLabel, autoreleaseFrequency: .workItem, target: DispatchQueue.global())
        self.timeRange = CMTimeRange(start: CMTime.zero, end: CMTime.positiveInfinity)
        super.init()
    }
    
    deinit {
        self._writer = nil
        self._reader = nil
        self._pixelBufferAdaptor = nil
        self._videoOutput = nil
        self._audioOutput = nil
        self._videoInput = nil
        self._audioInput = nil
    }
}

// MARK: - export

extension NextLevelSessionExporter {
    
    /// Completion handler type for when an export finishes.
    public typealias CompletionHandler = (Swift.Result<AVAssetExportSession.Status, Error>) -> Void
    
    /// Progress handler type
    public typealias ProgressHandler = (_ progress: Float) -> Void
    
    /// Render handler type for frame processing
    public typealias RenderHandler = (_ renderFrame: CVPixelBuffer, _ presentationTime: CMTime, _ resultingBuffer: CVPixelBuffer) -> Void
    
    /// Initiates an export session.
    ///
    /// - Parameter completionHandler: Handler called when an export session completes.
    /// - Throws: Failure indication thrown when an error has occurred during export.
    public func export(renderHandler: RenderHandler? = nil,
                       progressHandler: ProgressHandler? = nil,
                       completionHandler: CompletionHandler? = nil) {
        guard let asset = self.asset,
              let outputURL = self.outputURL,
              let outputFileType = self.outputFileType else {
            print("NextLevelSessionExporter, an asset and output URL are required for encoding")
            DispatchQueue.main.async {
                self._completionHandler?(.failure(NextLevelSessionExporterError.setupFailure))
            }
            return
        }
        
        if self._writer?.status == .writing {
            self._writer?.cancelWriting()
            self._writer = nil
        }
        
        if self._reader?.status == .reading {
            self._reader?.cancelReading()
            self._reader = nil
        }
        
        self._progress = 0
        
        do {
            self._reader = try AVAssetReader(asset: asset)
        } catch {
            print("NextLevelSessionExporter, could not setup a reader for the provided asset \(asset)")
            DispatchQueue.main.async {
                self._completionHandler?(.failure(NextLevelSessionExporterError.setupFailure))
            }
        }
        
        do {
            self._writer = try AVAssetWriter(outputURL: outputURL, fileType: outputFileType)
        } catch {
            print("NextLevelSessionExporter, could not setup a reader for the provided asset \(asset)")
            DispatchQueue.main.async {
                self._completionHandler?(.failure(NextLevelSessionExporterError.setupFailure))
            }
        }

        // if a video configuration exists, validate it (otherwise, proceed as audio)
        if let _ = self.videoOutputConfiguration, self.validateVideoOutputConfiguration() == false {
            print("NextLevelSessionExporter, could not setup with the specified video output configuration")
            DispatchQueue.main.async {
                self._completionHandler?(.failure(NextLevelSessionExporterError.setupFailure))
            }
        }
        
        self._progressHandler = progressHandler
        self._renderHandler = renderHandler
        self._completionHandler = completionHandler
        
        self._reader?.timeRange = self.timeRange
        self._writer?.shouldOptimizeForNetworkUse = self.optimizeForNetworkUse
        
        if let metadata = self.metadata {
            self._writer?.metadata = metadata
        }
        
        if self.timeRange.duration.isValid && self.timeRange.duration.isPositiveInfinity == false {
            self._duration = CMTimeGetSeconds(self.timeRange.duration)
        } else {
            self._duration = CMTimeGetSeconds(asset.duration)
        }
        
        if self.videoOutputConfiguration?.keys.contains(AVVideoCodecKey) == false {
            print("NextLevelSessionExporter, warning a video output configuration codec wasn't specified")
            if #available(iOS 11.0, *) {
                self.videoOutputConfiguration?[AVVideoCodecKey] = AVVideoCodecType.h264
            } else {
                self.videoOutputConfiguration?[AVVideoCodecKey] = AVVideoCodecH264
            }
        }
        
        self.setupVideoOutput(withAsset: asset)
        self.setupAudioOutput(withAsset: asset)
        self.setupAudioInput()
        
        // export
        
        self._writer?.startWriting()
        self._reader?.startReading()
        self._writer?.startSession(atSourceTime: self.timeRange.start)
        
        let dispatchGroup = DispatchGroup()
    
        let videoTracks = asset.tracks(withMediaType: AVMediaType.video)
        if let videoInput = self._videoInput,
           let videoOutput = self._videoOutput,
           videoTracks.count > 0 {
            dispatchGroup.enter()
            videoInput.requestMediaDataWhenReady(on: self._inputQueue, using: {
                if self.encode(readySamplesFromReaderOutput: videoOutput, toWriterInput: videoInput) == false {
                    dispatchGroup.leave()
                }
            })
        } 
        
        if let audioInput = self._audioInput,
            let audioOutput = self._audioOutput {
            dispatchGroup.enter()
            audioInput.requestMediaDataWhenReady(on: self._inputQueue, using: {
                if self.encode(readySamplesFromReaderOutput: audioOutput, toWriterInput: audioInput) == false {
                    dispatchGroup.leave()
                }
            })
        } 
        
        dispatchGroup.notify(queue: .global()) {
            DispatchQueue.main.async {
                self.finish()
            }
        }
    }
    
    /// Cancels any export in progress.
    public func cancelExport() {
        self._inputQueue.async {
            if self._writer?.status == .writing {
                self._writer?.cancelWriting()
            }
            
            if self._reader?.status == .reading {
                self._reader?.cancelReading()
            }
            
            DispatchQueue.main.async {
                self.complete()
                self.reset()
            }
        }
    }
    
}

// MARK: - setup funcs

extension NextLevelSessionExporter {
    
    private func setupVideoOutput(withAsset asset: AVAsset) {
        let videoTracks = asset.tracks(withMediaType: AVMediaType.video)
        
        guard videoTracks.count > 0 else {
            return
        }
        
        self._videoOutput = AVAssetReaderVideoCompositionOutput(videoTracks: videoTracks, videoSettings: self.videoInputConfiguration)
        self._videoOutput?.alwaysCopiesSampleData = false
        
        if let videoComposition = self.videoComposition {
            self._videoOutput?.videoComposition = videoComposition
        } else {
            self._videoOutput?.videoComposition = self.createVideoComposition()
        }
        
        if let videoOutput = self._videoOutput,
            let reader = self._reader {
            if reader.canAdd(videoOutput) {
                reader.add(videoOutput)
            }
        }
        
        // video input
        if self._writer?.canApply(outputSettings: self.videoOutputConfiguration, forMediaType: AVMediaType.video) == true {
            self._videoInput = AVAssetWriterInput(mediaType: AVMediaType.video, outputSettings: self.videoOutputConfiguration)
            self._videoInput?.expectsMediaDataInRealTime = self.expectsMediaDataInRealTime
        } else {
            print("Unsupported output configuration")
            return
        }
        
        if let writer = self._writer,
            let videoInput = self._videoInput {
            if writer.canAdd(videoInput) {
                writer.add(videoInput)
            }
            
            // setup pixelbuffer adaptor
            
            var pixelBufferAttrib: [String : Any] = [:]
            pixelBufferAttrib[kCVPixelBufferPixelFormatTypeKey as String] = NSNumber(integerLiteral: Int(kCVPixelFormatType_32RGBA))
            if let videoComposition = self._videoOutput?.videoComposition {
                pixelBufferAttrib[kCVPixelBufferWidthKey as String] = NSNumber(integerLiteral: Int(videoComposition.renderSize.width))
                pixelBufferAttrib[kCVPixelBufferHeightKey as String] = NSNumber(integerLiteral: Int(videoComposition.renderSize.height))
            }
            pixelBufferAttrib["IOSurfaceOpenGLESTextureCompatibility"] = NSNumber(booleanLiteral:  true)
            pixelBufferAttrib["IOSurfaceOpenGLESFBOCompatibility"] = NSNumber(booleanLiteral:  true)
            
            self._pixelBufferAdaptor = AVAssetWriterInputPixelBufferAdaptor(assetWriterInput: videoInput, sourcePixelBufferAttributes: pixelBufferAttrib)
        }
    }
    
    private func setupAudioOutput(withAsset asset: AVAsset) {
        let audioTracks = asset.tracks(withMediaType: AVMediaType.audio)
        var audioTracksToUse: [AVAssetTrack] = []
        
        guard audioTracks.count > 0 else {
            self._audioOutput = nil
            return
        }
        // Remove APAC tracks
        for audioTrack in audioTracks {
            let mediaSubtypes = audioTrack.formatDescriptions.filter { CMFormatDescriptionGetMediaType($0 as! CMFormatDescription) == kCMMediaType_Audio }.map { CMFormatDescriptionGetMediaSubType($0 as! CMFormatDescription) }
            for mediaSubtype in mediaSubtypes where mediaSubtype != kAudioFormatAPAC {
                audioTracksToUse.append(audioTrack)
            }
        }
        self._audioOutput = AVAssetReaderAudioMixOutput(audioTracks: audioTracksToUse, audioSettings: nil)
        self._audioOutput?.alwaysCopiesSampleData = false
        self._audioOutput?.audioMix = self.audioMix
        if let reader = self._reader,
            let audioOutput = self._audioOutput {
            if reader.canAdd(audioOutput) {
                reader.add(audioOutput)
            }
        }
    }
    
    private func setupAudioInput() {
        guard let _ = self._audioOutput else {
            return
        }
        
        self._audioInput = AVAssetWriterInput(mediaType: AVMediaType.audio, outputSettings: self.audioOutputConfiguration)
        self._audioInput?.expectsMediaDataInRealTime = self.expectsMediaDataInRealTime
        if let writer = self._writer, let audioInput = self._audioInput {
            if writer.canAdd(audioInput) {
                writer.add(audioInput)
            }
        }
    }
    
}

// MARK: - internal funcs

extension NextLevelSessionExporter {
    
    // called on the inputQueue
    internal func encode(readySamplesFromReaderOutput output: AVAssetReaderOutput, toWriterInput input: AVAssetWriterInput) -> Bool {
        while input.isReadyForMoreMediaData {
            guard self._reader?.status == .reading && self._writer?.status == .writing,
                  let sampleBuffer = output.copyNextSampleBuffer() else {
                input.markAsFinished()
                return false
            }
            
            var handled = false
            var error = false
            if self._videoOutput == output {
                // determine progress
                self._lastSamplePresentationTime = CMSampleBufferGetPresentationTimeStamp(sampleBuffer) - self.timeRange.start
                let progress = self._duration == 0 ? 1 : Float(CMTimeGetSeconds(self._lastSamplePresentationTime) / self._duration)
                self.updateProgress(progress: progress)
                
                // prepare progress frames
                if let pixelBufferAdaptor = self._pixelBufferAdaptor,
                    let pixelBufferPool = pixelBufferAdaptor.pixelBufferPool,
                    let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) {
                    
                    var toRenderBuffer: CVPixelBuffer? = nil
                    let result = CVPixelBufferPoolCreatePixelBuffer(kCFAllocatorDefault, pixelBufferPool, &toRenderBuffer)
                    if result == kCVReturnSuccess {
                        if let toBuffer = toRenderBuffer {
                            self._renderHandler?(pixelBuffer, self._lastSamplePresentationTime, toBuffer)
                            if pixelBufferAdaptor.append(toBuffer, withPresentationTime:self._lastSamplePresentationTime) == false {
                                error = true
                            }
                            handled = true
                        }
                    }
                }
            }
            
            if handled == false && input.append(sampleBuffer) == false {
                error = true
            }
            
            if error {
                return false
            }
        }
        return true
    }
    
    internal func createVideoComposition() -> AVMutableVideoComposition {
        let videoComposition = AVMutableVideoComposition()
        
        if let asset = self.asset,
            let videoTrack = asset.tracks(withMediaType: AVMediaType.video).first {
            
            // determine the framerate
            
            var frameRate: Float = 0
            if let videoConfiguration = self.videoOutputConfiguration {
                if let videoCompressionConfiguration = videoConfiguration[AVVideoCompressionPropertiesKey] as? [String: Any] {
                    if let trackFrameRate = videoCompressionConfiguration[AVVideoAverageNonDroppableFrameRateKey] as? NSNumber {
                        frameRate = trackFrameRate.floatValue
                    }
                }
            } else {
                frameRate = videoTrack.nominalFrameRate
            }
            
            if frameRate == 0 {
                frameRate = 30
            }
            videoComposition.frameDuration = CMTimeMake(value: 1, timescale: Int32(frameRate))
            
            // determine the appropriate size and transform
            
            if let videoConfiguration = self.videoOutputConfiguration {
                
                let videoWidth = videoConfiguration[AVVideoWidthKey] as? NSNumber
                let videoHeight = videoConfiguration[AVVideoHeightKey] as? NSNumber
                
                // validated to be non-nil byt this point
                let width = videoWidth!.intValue
                let height = videoHeight!.intValue
                
                let targetSize = CGSize(width: width, height: height)
                var naturalSize = videoTrack.naturalSize
                
                var transform = videoTrack.preferredTransform
                
                let rect = CGRect(x: 0, y: 0, width: naturalSize.width, height: naturalSize.height)
                let transformedRect = rect.applying(transform)
                // transformedRect should have origin at 0 if correct; otherwise add offset to correct it
                transform.tx -= transformedRect.origin.x;
                transform.ty -= transformedRect.origin.y;
                
                
                let videoAngleInDegrees = atan2(transform.b, transform.a) * 180 / .pi
                if videoAngleInDegrees == 90 || videoAngleInDegrees == -90 {
                    let tempWidth = naturalSize.width
                    naturalSize.width = naturalSize.height
                    naturalSize.height = tempWidth
                }
                videoComposition.renderSize = naturalSize
                
                // center the video
                
                var ratio: CGFloat = 0
                let xRatio: CGFloat = targetSize.width / naturalSize.width
                let yRatio: CGFloat = targetSize.height / naturalSize.height
                ratio = min(xRatio, yRatio)
                
                let postWidth = naturalSize.width * ratio
                let postHeight = naturalSize.height * ratio
                let transX = (targetSize.width - postWidth) * 0.5
                let transY = (targetSize.height - postHeight) * 0.5
                
                var matrix = CGAffineTransform(translationX: (transX / xRatio), y: (transY / yRatio))
                matrix = matrix.scaledBy(x: (ratio / xRatio), y: (ratio / yRatio))
                transform = transform.concatenating(matrix)
                
                // make the composition
                
                let compositionInstruction = AVMutableVideoCompositionInstruction()
                compositionInstruction.timeRange = CMTimeRange(start: CMTime.zero, duration: asset.duration)
                
                let layerInstruction = AVMutableVideoCompositionLayerInstruction(assetTrack: videoTrack)
                layerInstruction.setTransform(transform, at: CMTime.zero)
                
                compositionInstruction.layerInstructions = [layerInstruction]
                videoComposition.instructions = [compositionInstruction]
                
            }
        }
        
        return videoComposition
    }
    
    internal func updateProgress(progress: Float) {
        self.willChangeValue(forKey: "progress")
        self._progress = progress
        self.didChangeValue(forKey: "progress")
        self._progressHandler?(progress)
    }
    
    // always called on the main thread
    internal func finish() {
        if self._reader?.status == .cancelled || self._writer?.status == .cancelled {
            self.complete()
        } else if self._writer?.status == .failed {
            self._reader?.cancelReading()
            self.complete()
        } else if self._reader?.status == .failed {
            self._writer?.cancelWriting()
            self.complete()
        } else {
            self._writer?.finishWriting {
                self.complete()
            }
        }
    }
    
    // always called on the main thread
    internal func complete() {
        if self._reader?.status == .cancelled || self._writer?.status == .cancelled {
            guard let outputURL = self.outputURL else {
                self._completionHandler?(.failure(NextLevelSessionExporterError.cancelled))
                return
            }
            if FileManager.default.fileExists(atPath: outputURL.absoluteString) {
                try? FileManager.default.removeItem(at: outputURL)
            }
            self._completionHandler?(.failure(NextLevelSessionExporterError.cancelled))
            return
        }
        
        guard let reader = self._reader else {
            self._completionHandler?(.failure(NextLevelSessionExporterError.setupFailure))
            self._completionHandler = nil
            return
        }
        
        guard let writer = self._writer else {
            self._completionHandler?(.failure(NextLevelSessionExporterError.setupFailure))
            self._completionHandler = nil
            return
        }
        
        switch reader.status {
        case .failed:
            guard let outputURL = self.outputURL else {
                self._completionHandler?(.failure(reader.error ?? NextLevelSessionExporterError.readingFailure))
                return
            }
            if FileManager.default.fileExists(atPath: outputURL.absoluteString) {
                try? FileManager.default.removeItem(at: outputURL)
            }
            self._completionHandler?(.failure(reader.error ?? NextLevelSessionExporterError.readingFailure))
            return
        default:
            // do nothing
            break
        }
        
        switch writer.status {
        case .failed:
            guard let outputURL = self.outputURL else {
                self._completionHandler?(.failure(writer.error ?? NextLevelSessionExporterError.writingFailure))
                return
            }
            if FileManager.default.fileExists(atPath: outputURL.absoluteString) {
                try? FileManager.default.removeItem(at: outputURL)
            }
            self._completionHandler?(.failure(writer.error ?? NextLevelSessionExporterError.writingFailure))
            return
        default:
            // do nothing
            break
        }

        self._completionHandler?(.success(self.status))
        self._completionHandler = nil
    }
    
    // subclass and add more checks, if needed
    open func validateVideoOutputConfiguration() -> Bool {
        guard let videoOutputConfiguration = self.videoOutputConfiguration else {
            return false
        }

        let videoWidth = videoOutputConfiguration[AVVideoWidthKey] as? NSNumber
        let videoHeight = videoOutputConfiguration[AVVideoHeightKey] as? NSNumber
        if videoWidth == nil || videoHeight == nil {
            return false
        }
                
        return true
    }
    
    internal func reset() {
        self._progress = 0
        self._writer = nil
        self._reader = nil
        self._pixelBufferAdaptor = nil
        
        self._videoOutput = nil
        self._audioOutput = nil
        self._videoInput = nil
        self._audioInput = nil

        self._progressHandler = nil
        self._renderHandler = nil
        self._completionHandler = nil
    }
    
}

// MARK: - AVAsset extension

extension AVAsset {

    /// Initiates a NextLevelSessionExport on the asset
    ///
    /// - Parameters:
    ///   - outputFileType: type of resulting file to create
    ///   - outputURL: location of resulting file
    ///   - metadata: data to embed in the result
    ///   - videoInputConfiguration: video input configuration
    ///   - videoOutputConfiguration: video output configuration
    ///   - audioOutputConfiguration: audio output configuration
    ///   - progressHandler: progress fraction handler
    ///   - completionHandler: completion handler
    public func nextlevel_export(outputFileType: AVFileType? = AVFileType.mp4,
                                   outputURL: URL,
                                   metadata: [AVMetadataItem]? = nil,
                                   videoInputConfiguration: [String : Any]? = nil,
                                   videoOutputConfiguration: [String : Any],
                                   audioOutputConfiguration: [String : Any],
                                   progressHandler: NextLevelSessionExporter.ProgressHandler? = nil,
                                   completionHandler: NextLevelSessionExporter.CompletionHandler? = nil) {
        let exporter = NextLevelSessionExporter(withAsset: self)
        exporter.outputFileType = outputFileType
        exporter.outputURL = outputURL
        exporter.videoOutputConfiguration = videoOutputConfiguration
        exporter.audioOutputConfiguration = audioOutputConfiguration
        exporter.export(progressHandler: progressHandler, completionHandler: completionHandler)
    }
    
}
