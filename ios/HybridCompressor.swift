//
//  HybridCompressor.swift
//  react-native-compressor
//
//  Nitro HybridObject implementation of the single `Compressor` native module.
//  It is a thin binding layer: it converts Nitro's `AnyMap` options into the
//  `NSDictionary`/`[String: Any]` the existing domain code already consumes,
//  bridges the Nitro `Promise` to the domain layer's `RCTPromiseResolveBlock`/
//  `RCTPromiseRejectBlock`, and registers progress callbacks (keyed by `uuid`)
//  with `EventEmitterHandler`. All heavy logic stays in the domain classes.
//

import Foundation
import NitroModules

// Previously imported from React. We define them locally so this Swift module no
// longer needs to `import React`: under Nitro's Swift↔C++ interop, importing
// React pulls move-only C++ types (e.g. jsinspector's RuntimeSamplingProfile)
// into Swift's importer and fails to compile. These blocks are created here and
// consumed by the domain layer entirely in Swift, so plain closures suffice.
typealias RCTPromiseResolveBlock = (Any?) -> Void
typealias RCTPromiseRejectBlock = (String?, String?, (any Error)?) -> Void

private let videoCompressor = VideoCompressor()
private let uploader = Uploader()

final class HybridCompressor: HybridCompressorSpec {
    // MARK: - Image

    func image_compress(imagePath: String, optionMap: AnyMap, onDownloadProgress: ((Double) -> Void)?) throws -> Promise<String> {
        let promise = Promise<String>()
        var options = dictionary(from: optionMap)
        // Remote-image download progress is keyed by `uuid` inside the Downloader. The JS
        // layer no longer sends one (the callback is passed directly), so mint one here.
        let uuid = (options["uuid"] as? String) ?? UUID().uuidString
        options["uuid"] = uuid
        EventEmitterHandler.registerDownloadProgress(uuid: uuid, onDownloadProgress)
        ImageMain.image_compress(imagePath, optionMap: options as NSDictionary, resolve: resolveString(promise, uuid), reject: reject(promise, uuid))
        return promise
    }

    func getImageMetaData(filePath: String) throws -> Promise<AnyMap> {
        let promise = Promise<AnyMap>()
        ImageMain.getImageMetaData(filePath, resolve: resolveAnyMap(promise, nil), reject: reject(promise, nil))
        return promise
    }

    // MARK: - Video

    func compress(fileUrl: String, optionMap: AnyMap, onProgress: ((Double) -> Void)?, onDownloadProgress: ((Double) -> Void)?) throws -> Promise<String> {
        let promise = Promise<String>()
        let options = dictionary(from: optionMap)
        let uuid = (options["uuid"] as? String) ?? ""
        EventEmitterHandler.registerVideoCompressProgress(uuid: uuid, onProgress)
        EventEmitterHandler.registerDownloadProgress(uuid: uuid, onDownloadProgress)
        videoCompressor.compress(fileUrl: fileUrl, options: options, resolve: resolveString(promise, uuid), reject: reject(promise, uuid))
        return promise
    }

    func cancelCompression(uuid: String) throws {
        videoCompressor.cancelCompression(uuid: uuid)
    }

    func getVideoMetaData(filePath: String) throws -> Promise<AnyMap> {
        let promise = Promise<AnyMap>()
        videoCompressor.getVideoMetaData(filePath, resolve: resolveAnyMap(promise, nil), reject: reject(promise, nil))
        return promise
    }

    func activateBackgroundTask(options: AnyMap, onExpired: (() -> Void)?) throws -> Promise<String> {
        let promise = Promise<String>()
        EventEmitterHandler.setBackgroundTaskExpiredCallback(onExpired)
        videoCompressor.activateBackgroundTask(options: dictionary(from: options), resolve: resolveString(promise, nil), reject: reject(promise, nil))
        return promise
    }

    func deactivateBackgroundTask(options: AnyMap) throws -> Promise<String> {
        let promise = Promise<String>()
        EventEmitterHandler.setBackgroundTaskExpiredCallback(nil)
        videoCompressor.deactivateBackgroundTask(options: dictionary(from: options), resolve: resolveString(promise, nil), reject: reject(promise, nil))
        return promise
    }

    // MARK: - Audio

    func compress_audio(fileUrl: String, optionMap: AnyMap) throws -> Promise<String> {
        let promise = Promise<String>()
        AudioMain.compress_audio(fileUrl, optionMap: dictionary(from: optionMap) as NSDictionary, resolve: resolveString(promise, nil), reject: reject(promise, nil))
        return promise
    }

    // MARK: - Upload / Download

    func upload(fileUrl: String, options: AnyMap, onProgress: ((Double, Double) -> Void)?) throws -> Promise<AnyMap> {
        let promise = Promise<AnyMap>()
        let dict = dictionary(from: options)
        let uuid = (dict["uuid"] as? String) ?? ""
        EventEmitterHandler.registerUploadProgress(uuid: uuid, onProgress)
        uploader.upload(filePath: fileUrl, options: dict, resolve: resolveAnyMap(promise, uuid), reject: reject(promise, uuid))
        return promise
    }

    func cancelUpload(uuid: String, shouldCancelAll: Bool) throws {
        uploader.cancelUpload(uuid: uuid, shouldCancelAll: shouldCancelAll)
    }

    func download(fileUrl: String, options: AnyMap, onProgress: ((Double) -> Void)?) throws -> Promise<String> {
        let promise = Promise<String>()
        let dict = dictionary(from: options)
        let uuid = (dict["uuid"] as? String) ?? ""
        let progressDivider = (dict["progressDivider"] as? NSNumber)?.intValue ?? 0
        EventEmitterHandler.registerDownloadProgress(uuid: uuid, onProgress)
        Downloader.downloadFileAndSaveToCache(fileUrl, uuid: uuid, progressDivider: progressDivider) { downloadedPath in
            EventEmitterHandler.unregister(uuid: uuid)
            promise.resolve(withResult: downloadedPath)
        }
        return promise
    }

    // MARK: - Others

    func generateFilePath(fileExtension: String) throws -> Promise<String> {
        let promise = Promise<String>()
        Utils.generateFilePath(fileExtension, resolve: resolveString(promise, nil), reject: reject(promise, nil))
        return promise
    }

    func getRealPath(path: String, type: String) throws -> Promise<String> {
        let promise = Promise<String>()
        Utils.getRealPath(path, type: type, resolve: resolveString(promise, nil), reject: reject(promise, nil))
        return promise
    }

    func getFileSize(filePath: String) throws -> Promise<String> {
        let promise = Promise<String>()
        Utils.getFileSize(filePath, resolve: resolveString(promise, nil), reject: reject(promise, nil))
        return promise
    }

    func createVideoThumbnail(fileUrl: String, options: AnyMap) throws -> Promise<VideoThumbnailResult> {
        let promise = Promise<VideoThumbnailResult>()
        let thumbnail = CreateVideoThumbnail()
        thumbnail.create(fileUrl, options: dictionary(from: options) as NSDictionary, resolve: { value in
            let dict = HybridCompressor.stringKeyedDictionary(value)
            let result = VideoThumbnailResult(
                path: dict["path"] as? String ?? "",
                size: HybridCompressor.doubleValue(dict["size"]),
                mime: dict["mime"] as? String ?? "",
                width: HybridCompressor.doubleValue(dict["width"]),
                height: HybridCompressor.doubleValue(dict["height"])
            )
            promise.resolve(withResult: result)
        }, rejecter: reject(promise, nil))
        return promise
    }

    func clearCache(cacheDir: String?) throws -> Promise<String> {
        let promise = Promise<String>()
        CreateVideoThumbnail.cleanCacheDir(cacheDir: cacheDir ?? "", resolve: resolveString(promise, nil), rejecter: reject(promise, nil))
        return promise
    }

    // MARK: - Helpers

    /// Convert a Nitro `AnyMap` to a `[String: Any]`, round-tripping through
    /// `NSDictionary` so numbers are boxed as `NSNumber` (the domain parsers rely
    /// on `as? Int` / `as? Bool`, which fail on a bare Swift `Double`).
    private func dictionary(from map: AnyMap) -> [String: Any] {
        let normalized = (HybridCompressor.normalize(map.toDictionary()) as? [String: Any]) ?? [:]
        return ((normalized as NSDictionary) as? [String: Any]) ?? normalized
    }

    private func resolveString(_ promise: Promise<String>, _ uuid: String?) -> RCTPromiseResolveBlock {
        return { value in
            if let uuid = uuid { EventEmitterHandler.unregister(uuid: uuid) }
            promise.resolve(withResult: value as? String ?? "")
        }
    }

    private func resolveAnyMap(_ promise: Promise<AnyMap>, _ uuid: String?) -> RCTPromiseResolveBlock {
        return { value in
            if let uuid = uuid { EventEmitterHandler.unregister(uuid: uuid) }
            promise.resolve(withResult: AnyMap.fromDictionaryIgnoreIncompatible(HybridCompressor.stringKeyedOptionalDictionary(value)))
        }
    }

    private func reject<T>(_ promise: Promise<T>, _ uuid: String?) -> RCTPromiseRejectBlock {
        return { code, message, error in
            if let uuid = uuid { EventEmitterHandler.unregister(uuid: uuid) }
            let nsError = error ?? RuntimeError.error(withMessage: message ?? code ?? "react-native-compressor error")
            promise.reject(withError: nsError)
        }
    }

    /// Recursively strip optionals so a `[String: Any?]` (what `AnyMap.toDictionary()`
    /// returns, including nested objects/arrays) becomes a plain `[String: Any]`.
    private static func normalize(_ value: Any?) -> Any? {
        switch value {
        case let dict as [String: Any?]:
            var out = [String: Any]()
            for (key, nested) in dict {
                if let nestedValue = normalize(nested) { out[key] = nestedValue }
            }
            return out
        case let array as [Any?]:
            return array.compactMap { normalize($0) }
        default:
            return value
        }
    }

    private static func stringKeyedDictionary(_ value: Any?) -> [String: Any] {
        if let dict = value as? [String: Any] { return dict }
        if let ns = value as? NSDictionary {
            var out = [String: Any]()
            for (key, val) in ns where key is String {
                out[key as! String] = val
            }
            return out
        }
        return [:]
    }

    private static func stringKeyedOptionalDictionary(_ value: Any?) -> [String: Any?] {
        if let ns = value as? NSDictionary {
            var out = [String: Any?]()
            for (key, val) in ns where key is String {
                out[key as! String] = val
            }
            return out
        }
        if let dict = value as? [String: Any] {
            var out = [String: Any?]()
            for (key, val) in dict { out[key] = val }
            return out
        }
        return [:]
    }

    private static func doubleValue(_ value: Any?) -> Double {
        if let number = value as? NSNumber { return number.doubleValue }
        if let double = value as? Double { return double }
        if let float = value as? Float { return Double(float) }
        if let int = value as? Int { return Double(int) }
        return 0
    }
}
