//
//  AudioMain.swift
//  react-native-compressor
//
//  Created by Numan on 10/09/2023.
//

import Foundation
import AVFoundation
let AlAsset_Library_Scheme = "assets-library"
class AudioMain{
    static func compress_audio(_ fileUrl: String, optionMap: NSDictionary, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        do {
            var fileUrl = fileUrl
            if fileUrl.hasPrefix("file://") {
                fileUrl = fileUrl.replacingOccurrences(of: "file://", with: "")
            }
            let fileManager = FileManager.default
            var isDir: ObjCBool = false
            if !fileManager.fileExists(atPath: fileUrl, isDirectory: &isDir) || isDir.boolValue {
                let err = NSError(domain: "file not found", code: -15, userInfo: nil)
                reject(String(err.code), err.localizedDescription, err)
                return
            }
            
            let assetOptions: [String: Any] = [AVURLAssetPreferPreciseDurationAndTimingKey: true]
            let asset = AVURLAsset(url: URL(fileURLWithPath: fileUrl), options: assetOptions)
            let quality = optionMap["quality"] as? String ?? ""
            let qualityConstant = getAudioQualityConstant(quality)
            auto_compress_helper(asset, qualityConstant: qualityConstant) { mp3Path, finished in
                if finished {
                    resolve("file://\(mp3Path ?? "")")
                } else {
                    reject("Error", "Something went wrong", nil)
                }
            }
        } catch {
            reject(error.localizedDescription, error.localizedDescription, nil)
        }
    }
    
    static func getAudioQualityConstant(_ quality: String) -> String {
        let audioQualityArray = ["low", "medium", "high"]
        if let index = audioQualityArray.firstIndex(of: quality) {
            switch index {
            case 0:
                return AVAssetExportPresetLowQuality
            case 1:
                return AVAssetExportPresetMediumQuality
            case 2:
                return AVAssetExportPresetHighestQuality
            default:
                return AVAssetExportPresetMediumQuality
            }
        }
        return AVAssetExportPresetMediumQuality
    }
    
    static func auto_compress_helper(_ avAsset: AVURLAsset, qualityConstant: String, complete completeCallback: @escaping (_ mp3Path: String?, _ finished: Bool) -> Void) {
        var path: String
        if avAsset.url.scheme == AlAsset_Library_Scheme {
            path = avAsset.url.query ?? ""
            if path.isEmpty {
                completeCallback(nil, false)
                return
            }
        } else {
            path = avAsset.url.path
            if path.isEmpty || !FileManager.default.fileExists(atPath: path) {
                completeCallback(nil, false)
                return
            }
        }
        
        let mp3Path = Utils.generateCacheFilePath("m4a")
        
        if FileManager.default.fileExists(atPath: mp3Path) {
            completeCallback(mp3Path, true)
            return
        }
        
        let compatiblePresets = AVAssetExportSession.exportPresets(compatibleWith: avAsset)
        
        if compatiblePresets.contains(qualityConstant) {
            let exportSession = AVAssetExportSession(asset: avAsset, presetName: AVAssetExportPresetAppleM4A)
            
            let mp3Url = URL(fileURLWithPath: mp3Path)
            exportSession?.outputURL = mp3Url
            exportSession?.shouldOptimizeForNetworkUse = true
            exportSession?.outputFileType = .m4a
            
            exportSession?.exportAsynchronously(completionHandler: {
                var finished = false
                switch exportSession?.status {
                case .failed:
                    print("AVAssetExportSessionStatusFailed, error: \(exportSession?.error?.localizedDescription ?? "").")
                case .cancelled:
                    print("AVAssetExportSessionStatusCancelled.")
                case .completed:
                    print("AVAssetExportSessionStatusCompleted.")
                    finished = true
                case .unknown:
                    print("AVAssetExportSessionStatusUnknown")
                case .waiting:
                    print("AVAssetExportSessionStatusWaiting")
                case .exporting:
                    print("AVAssetExportSessionStatusExporting")
                default:
                    break
                }
                completeCallback(mp3Path, finished)
            })
        }
    }
}
