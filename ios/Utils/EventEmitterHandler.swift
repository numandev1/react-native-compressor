//
//  EventEmitterHandler.swift
//  react-native-compressor
//
//  Created by Numan on 09/09/2023.
//

import Foundation
import UIKit

/// Routes native progress emissions to the per-call JS callbacks that
/// `HybridCompressor` registers. This replaces the old `RCTEventEmitter` bridge:
/// Nitro delivers progress through callback parameters, so the domain layer's
/// `emit*` calls (unchanged) are dispatched to the callback registered under the
/// same `uuid` that the JS layer threads through the options map.
class EventEmitterHandler {
    private static let lock = NSLock()
    private static var videoCompressProgressCallbacks = [String: (Double) -> Void]()
    private static var downloadProgressCallbacks = [String: (Double) -> Void]()
    private static var uploadProgressCallbacks = [String: (Double, Double) -> Void]()
    private static var backgroundTaskExpiredCallback: (() -> Void)?

    // MARK: - Registration (called by HybridCompressor)

    static func registerVideoCompressProgress(uuid: String, _ callback: ((Double) -> Void)?) {
        guard let callback = callback else { return }
        lock.lock(); defer { lock.unlock() }
        videoCompressProgressCallbacks[uuid] = callback
    }

    static func registerDownloadProgress(uuid: String, _ callback: ((Double) -> Void)?) {
        guard let callback = callback else { return }
        lock.lock(); defer { lock.unlock() }
        downloadProgressCallbacks[uuid] = callback
    }

    static func registerUploadProgress(uuid: String, _ callback: ((Double, Double) -> Void)?) {
        guard let callback = callback else { return }
        lock.lock(); defer { lock.unlock() }
        uploadProgressCallbacks[uuid] = callback
    }

    static func unregister(uuid: String) {
        lock.lock(); defer { lock.unlock() }
        videoCompressProgressCallbacks[uuid] = nil
        downloadProgressCallbacks[uuid] = nil
        uploadProgressCallbacks[uuid] = nil
    }

    static func setBackgroundTaskExpiredCallback(_ callback: (() -> Void)?) {
        lock.lock(); defer { lock.unlock() }
        backgroundTaskExpiredCallback = callback
    }

    // MARK: - Emission (called by the domain layer — method names preserved)

    static func emitDownloadProgress(_ progress: NSNumber, uuid: String) {
        lock.lock(); let callback = downloadProgressCallbacks[uuid]; lock.unlock()
        callback?(progress.doubleValue)
    }

    static func emitVideoCompressProgress(_ progress: Float, uuid: String) {
        lock.lock(); let callback = videoCompressProgressCallbacks[uuid]; lock.unlock()
        callback?(Double(progress))
    }

    static func emituploadProgress(_ uuid: String, totalBytesSent: Int64, totalBytesExpectedToSend: Int64) {
        lock.lock(); let callback = uploadProgressCallbacks[uuid]; lock.unlock()
        callback?(Double(totalBytesSent), Double(totalBytesExpectedToSend))
    }

    static func emitBackgroundTaskExpired(_ backgroundTaskId: UIBackgroundTaskIdentifier) {
        lock.lock(); let callback = backgroundTaskExpiredCallback; lock.unlock()
        callback?()
    }
}
