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
    case unsupportedVideoOutputConfiguration
    case missingVideoTrackInOutput

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
            case .unsupportedVideoOutputConfiguration:
                return "The writer rejected the video output configuration"
            case .missingVideoTrackInOutput:
                return "Export finished without a video track in the output"
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

    /// When true, audio tracks are excluded from the export entirely.
    public var stripAudio: Bool = false
    
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

    // Settlement bookkeeping: every export calls the completion handler
    // exactly once. `_completionHandler` and `_createdOutput` are read on the main
    // queue; the fields below are only touched on `_inputQueue`.
    fileprivate enum TrackKind { case video, audio }
    fileprivate var _createdOutput = false
    fileprivate var _pendingTracks: Set<TrackKind> = []
    fileprivate var _trackGroup: DispatchGroup?
    fileprivate var _cancelRequested = false
    fileprivate var _abortError: Error?
    fileprivate var _watchdog: DispatchSourceTimer?

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
        self._watchdog?.cancel()
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
        // Assign the handlers before any failure path, so early failures still
        // reach the completion handler.
        self._progressHandler = progressHandler
        self._renderHandler = renderHandler
        self._completionHandler = completionHandler

        guard let asset = self.asset,
              let outputURL = self.outputURL,
              let outputFileType = self.outputFileType else {
            print("NextLevelSessionExporter, an asset and output URL are required for encoding")
            self.fail(NextLevelSessionExporterError.setupFailure)
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
        
        let reader: AVAssetReader
        do {
            reader = try AVAssetReader(asset: asset)
        } catch {
            print("NextLevelSessionExporter, could not setup a reader for the provided asset \(asset)")
            self.fail(NextLevelSessionExporterError.setupFailure)
            return
        }
        
        let writer: AVAssetWriter
        do {
            writer = try AVAssetWriter(outputURL: outputURL, fileType: outputFileType)
        } catch {
            print("NextLevelSessionExporter, could not setup a writer for the provided asset \(asset)")
            self.fail(NextLevelSessionExporterError.setupFailure)
            return
        }
        self._reader = reader
        self._writer = writer
        // AVAssetWriter refuses an existing file, so anything at `outputURL` from
        // here on is ours to delete on failure.
        self._createdOutput = true

        // if a video configuration exists, validate it (otherwise, proceed as audio)
        if let _ = self.videoOutputConfiguration, self.validateVideoOutputConfiguration() == false {
            print("NextLevelSessionExporter, could not setup with the specified video output configuration")
            self.fail(NextLevelSessionExporterError.setupFailure)
            return
        }
        
        reader.timeRange = self.timeRange
        writer.shouldOptimizeForNetworkUse = self.optimizeForNetworkUse
        
        if let metadata = self.metadata {
            writer.metadata = metadata
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
        
        // Fail loudly when the writer rejects the video output configuration.
        // Continuing here used to export only the audio track and still resolve
        // as a success (issue #400).
        guard self.setupVideoOutput(withAsset: asset) else {
            self.fail(NextLevelSessionExporterError.unsupportedVideoOutputConfiguration)
            return
        }
        if !self.stripAudio {
            // An audio output the reader holds but no writer input drains
            // stalls the other track, so a rejected audio pairing fails the export.
            guard self.setupAudio(withAsset: asset) else {
                self.fail(NextLevelSessionExporterError.setupFailure)
                return
            }
        }
        
        var tracks: [(TrackKind, AVAssetReaderOutput, AVAssetWriterInput)] = []
        if let videoInput = self._videoInput, let videoOutput = self._videoOutput {
            tracks.append((.video, videoOutput, videoInput))
        }
        if let audioInput = self._audioInput, let audioOutput = self._audioOutput {
            tracks.append((.audio, audioOutput, audioInput))
        }
        
        // export
        
        guard writer.startWriting() else {
            self.fail(writer.error ?? NextLevelSessionExporterError.writingFailure)
            return
        }
        guard reader.startReading() else {
            writer.cancelWriting()
            self.fail(reader.error ?? NextLevelSessionExporterError.readingFailure)
            return
        }
        writer.startSession(atSourceTime: self.timeRange.start)
        
        // Each track leaves the group exactly once — when its samples run out,
        // or when any track, the watchdog or `cancelExport()` aborts the export.
        // A writer or reader failure can stop a track's ready callbacks, so the
        // group cannot rely on each track's own callback to leave.
        let group = DispatchGroup()
        self._inputQueue.sync {
            self._trackGroup = group
            for (kind, _, _) in tracks {
                group.enter()
                self._pendingTracks.insert(kind)
            }
            if !tracks.isEmpty {
                self.startWatchdog()
            }
        }
        for (kind, output, input) in tracks {
            input.requestMediaDataWhenReady(on: self._inputQueue, using: {
                self.pump(kind, from: output, to: input)
            })
        }
        
        group.notify(queue: .main) {
            self.finish()
        }
    }
    
    /// Cancels any export in progress. The completion handler is still called
    /// exactly once (with `.cancelled` unless the export had already finished).
    public func cancelExport() {
        self._inputQueue.async {
            self._cancelRequested = true
            if !self._pendingTracks.isEmpty {
                self.abortTracks(nil)
            }
        }
    }
    
}

// MARK: - setup funcs

extension NextLevelSessionExporter {
    
    /// Returns `false` when the asset has video tracks but a video writer input
    /// could not be created — exporting would silently produce an audio-only
    /// file (see numandev1/react-native-compressor#400).
    private func setupVideoOutput(withAsset asset: AVAsset) -> Bool {
        let videoTracks = asset.tracks(withMediaType: AVMediaType.video)

        guard videoTracks.count > 0 else {
            return true
        }
        
        self._videoOutput = AVAssetReaderVideoCompositionOutput(videoTracks: videoTracks, videoSettings: self.videoInputConfiguration)
        self._videoOutput?.alwaysCopiesSampleData = false
        
        if let videoComposition = self.videoComposition {
            self._videoOutput?.videoComposition = videoComposition
        } else {
            self._videoOutput?.videoComposition = self.createVideoComposition()
        }
        
        // A video output the reader refuses would never be drained (and
        // reading from it raises), so treat it like a rejected configuration.
        guard let videoOutput = self._videoOutput,
              let reader = self._reader,
              reader.canAdd(videoOutput) else {
            print("NextLevelSessionExporter, the reader rejected the video output")
            return false
        }
        reader.add(videoOutput)

        // video input
        if self._writer?.canApply(outputSettings: self.videoOutputConfiguration, forMediaType: AVMediaType.video) == true {
            self._videoInput = AVAssetWriterInput(mediaType: AVMediaType.video, outputSettings: self.videoOutputConfiguration)
            self._videoInput?.expectsMediaDataInRealTime = self.expectsMediaDataInRealTime
        } else {
            print("NextLevelSessionExporter, unsupported video output configuration, failing export instead of writing an audio-only file")
            return false
        }
        
        if let writer = self._writer,
            let videoInput = self._videoInput {
            // An input the writer refuses never becomes ready, so the
            // export would wait forever.
            guard writer.canAdd(videoInput) else {
                print("NextLevelSessionExporter, the writer rejected the video input")
                return false
            }
            writer.add(videoInput)

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
        return true
    }

    /// Sets up the audio reader output and writer input as a pair. Returns
    /// `true` without audio when the asset has no usable (non-APAC) audio track,
    /// and `false` when the reader or writer refuses its half — adding only one
    /// half leaves an undrained output or an input that never becomes ready, and
    /// the export waits forever.
    private func setupAudio(withAsset asset: AVAsset) -> Bool {
        self._audioOutput = nil
        self._audioInput = nil
        let audioTracks = asset.tracks(withMediaType: AVMediaType.audio)
        var audioTracksToUse: [AVAssetTrack] = []

        // Remove APAC tracks
        for audioTrack in audioTracks {
            let mediaSubtypes = audioTrack.formatDescriptions.filter { CMFormatDescriptionGetMediaType($0 as! CMFormatDescription) == kCMMediaType_Audio }.map { CMFormatDescriptionGetMediaSubType($0 as! CMFormatDescription) }
            for mediaSubtype in mediaSubtypes where mediaSubtype != kAudioFormatAPAC {
                audioTracksToUse.append(audioTrack)
            }
        }
        guard audioTracksToUse.count > 0 else {
            return true
        }
        let audioOutput = AVAssetReaderAudioMixOutput(audioTracks: audioTracksToUse, audioSettings: nil)
        audioOutput.alwaysCopiesSampleData = false
        audioOutput.audioMix = self.audioMix
        let audioInput = AVAssetWriterInput(mediaType: AVMediaType.audio, outputSettings: self.audioOutputConfiguration)
        audioInput.expectsMediaDataInRealTime = self.expectsMediaDataInRealTime
        guard let reader = self._reader,
              let writer = self._writer,
              reader.canAdd(audioOutput),
              writer.canAdd(audioInput) else {
            print("NextLevelSessionExporter, the reader or writer rejected the audio track")
            return false
        }
        reader.add(audioOutput)
        writer.add(audioInput)
        self._audioOutput = audioOutput
        self._audioInput = audioInput
        return true
    }
    
}

// MARK: - internal funcs

extension NextLevelSessionExporter {
    
    // Called on the input queue from the track's ready callback. Every exit
    // either keeps the track pending (the writer will call again when ready) or
    // settles it through `finishTrack` / `abortTracks`.
    fileprivate func pump(_ kind: TrackKind, from output: AVAssetReaderOutput, to input: AVAssetWriterInput) {
        while self._pendingTracks.contains(kind) && input.isReadyForMoreMediaData {
            if self._cancelRequested {
                self.abortTracks(nil)
                return
            }
            guard let reader = self._reader,
                  let writer = self._writer,
                  writer.status == .writing else {
                self.abortTracks(nil)
                return
            }
            switch reader.status {
            case .reading:
                break
            case .completed:
                self.finishTrack(kind, markingFinished: input)
                return
            default:
                self.abortTracks(nil)
                return
            }
            guard let sampleBuffer = output.copyNextSampleBuffer() else {
                // nil is both "this output is exhausted" and "the reader failed".
                if reader.status == .failed {
                    self.abortTracks(nil)
                } else {
                    self.finishTrack(kind, markingFinished: input)
                }
                return
            }
            if self.append(sampleBuffer, of: kind, to: input) == false {
                self.abortTracks(writer.error ?? NextLevelSessionExporterError.writingFailure)
                return
            }
        }
    }

    // called on the input queue — appends one sample (the former per-sample body of `encode`)
    fileprivate func append(_ sampleBuffer: CMSampleBuffer, of kind: TrackKind, to input: AVAssetWriterInput) -> Bool {
        if kind == .video {
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
                if result == kCVReturnSuccess, let toBuffer = toRenderBuffer {
                    self._renderHandler?(pixelBuffer, self._lastSamplePresentationTime, toBuffer)
                    return pixelBufferAdaptor.append(toBuffer, withPresentationTime: self._lastSamplePresentationTime)
                }
            }
        }
        return input.append(sampleBuffer)
    }

    // called on the input queue — the only place a track leaves the group
    fileprivate func finishTrack(_ kind: TrackKind, markingFinished input: AVAssetWriterInput?) {
        guard self._pendingTracks.remove(kind) != nil else {
            return
        }
        if let input = input, self._writer?.status == .writing {
            input.markAsFinished()
        }
        if self._pendingTracks.isEmpty {
            self._watchdog?.cancel()
            self._watchdog = nil
        }
        self._trackGroup?.leave()
    }

    // called on the input queue — stops reading and writing and settles every
    // pending track, so the group always notifies
    fileprivate func abortTracks(_ error: Error?) {
        if self._abortError == nil, let error = error {
            self._abortError = error
        }
        if self._reader?.status == .reading {
            self._reader?.cancelReading()
        }
        if self._writer?.status == .writing {
            self._writer?.cancelWriting()
        }
        for kind in self._pendingTracks {
            self.finishTrack(kind, markingFinished: nil)
        }
    }

    // called on the input queue. A writer or reader that fails (or a cancel that
    // arrives) while no track's ready callback is running would otherwise go
    // unnoticed — the writer stops calling a failed input's ready block.
    fileprivate func startWatchdog() {
        let timer = DispatchSource.makeTimerSource(queue: self._inputQueue)
        timer.schedule(deadline: .now() + .seconds(1), repeating: .seconds(1))
        timer.setEventHandler { [weak self] in
            guard let self = self, !self._pendingTracks.isEmpty else {
                return
            }
            let readerStatus = self._reader?.status
            if self._cancelRequested
                || self._writer?.status != .writing
                || (readerStatus != .reading && readerStatus != .completed) {
                self.abortTracks(nil)
            }
        }
        self._watchdog = timer
        timer.resume()
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
    
    // always called on the main thread, once, after every track settled
    internal func finish() {
        let (cancelRequested, abortError) = self._inputQueue.sync { (self._cancelRequested, self._abortError) }
        guard let reader = self._reader, let writer = self._writer else {
            self.complete()
            return
        }
        if cancelRequested
            || abortError != nil
            || writer.status != .writing
            || reader.status == .failed
            || reader.status == .cancelled {
            if reader.status == .reading {
                reader.cancelReading()
            }
            if writer.status == .writing {
                writer.cancelWriting()
            }
            self.complete()
            return
        }
        // `finishWriting` calls back on an arbitrary queue; hop to main so
        // `complete()` never races the main-queue paths.
        writer.finishWriting {
            DispatchQueue.main.async {
                self.complete()
            }
        }
    }
    
    // always called on the main thread
    internal func complete() {
        let (cancelRequested, abortError) = self._inputQueue.sync { (self._cancelRequested, self._abortError) }
        var failure: Error? = nil
        if cancelRequested {
            failure = NextLevelSessionExporterError.cancelled
        } else if let reader = self._reader, let writer = self._writer {
            if writer.status == .failed {
                failure = writer.error ?? NextLevelSessionExporterError.writingFailure
            } else if reader.status == .failed {
                failure = reader.error ?? NextLevelSessionExporterError.readingFailure
            } else if let abortError = abortError {
                failure = abortError
            } else if writer.status == .cancelled || reader.status == .cancelled {
                failure = NextLevelSessionExporterError.cancelled
            } else if writer.status != .completed {
                failure = NextLevelSessionExporterError.writingFailure
            } else if self.videoOutputConfiguration != nil,
                      let asset = self.asset,
                      let outputURL = self.outputURL,
                      asset.tracks(withMediaType: AVMediaType.video).count > 0,
                      AVAsset(url: outputURL).tracks(withMediaType: AVMediaType.video).count == 0 {
                // Guard against silently returning an audio-only file: when the source
                // has a video track and a video output was configured, the exported file
                // must contain a video track as well. A configuration the encoder rejects
                // at write time can still end with `.completed` while the video track is
                // dropped (issue #400) — surface that as an error instead of a success.
                failure = NextLevelSessionExporterError.missingVideoTrackInOutput
            }
        } else {
            failure = NextLevelSessionExporterError.setupFailure
        }
        
        if let failure = failure {
            self.removeOutput()
            self.deliver(.failure(failure))
        } else {
            self.deliver(.success(self.status))
        }
    }

    // A failure before any track started. Hops to main like every other
    // completion so the handler is never called re-entrantly from `export()`.
    fileprivate func fail(_ error: Error) {
        DispatchQueue.main.async {
            self.removeOutput()
            self.deliver(.failure(error))
        }
    }

    // always called on the main thread — the only caller of the completion handler
    fileprivate func deliver(_ result: Swift.Result<AVAssetExportSession.Status, Error>) {
        guard let completionHandler = self._completionHandler else {
            return
        }
        self._completionHandler = nil
        completionHandler(result)
    }

    // always called on the main thread. Removes the output file by URL (a
    // `fileExists(atPath: outputURL.absoluteString)` check never matches, since
    // that is a `file://` string, not a path).
    fileprivate func removeOutput() {
        guard self._createdOutput, let outputURL = self.outputURL, outputURL.isFileURL else {
            return
        }
        try? FileManager.default.removeItem(at: outputURL)
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
