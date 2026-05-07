//
//  CreateVideoThumbnail.swift
//  react-native-compressor
//
//  Created by Numan on 13/09/2023.
//

import Foundation
import AVFoundation
import UIKit

class CreateVideoThumbnail: NSObject {
  private static let defaultQuality = 0.9

    func create(_ fileUrl:String, options: NSDictionary, resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
    let headers = options["headers"] as? [String: Any] ?? [:]
    let format = "jpeg"

    do {
      // Prepare cache folder
      var tempDirectory = NSSearchPathForDirectoriesInDomains(.cachesDirectory, .userDomainMask, true).last ?? ""
      tempDirectory = (tempDirectory as NSString).appendingPathComponent("thumbnails/")
      // Create thumbnail directory if not exists
      try FileManager.default.createDirectory(atPath: tempDirectory, withIntermediateDirectories: true, attributes: nil)
      let fileName = "thumb-\(ProcessInfo.processInfo.globallyUniqueString).\(format)"
      let fullPath = (tempDirectory as NSString).appendingPathComponent(fileName)

      if FileManager.default.fileExists(atPath: fullPath) {
        if let imageData = try? Data(contentsOf: URL(fileURLWithPath: fullPath)) {
          if let thumbnail = UIImage(data: imageData) {
            resolve([
              "path": fullPath,
              "size": Float(imageData.count),
              "mime": "image/\(format)",
              "width": Float(thumbnail.size.width),
              "height": Float(thumbnail.size.height)
            ])
            return
          }
        }
      }

      var vidURL: URL?
      let fileUrlLowerCase = fileUrl.lowercased()

      if fileUrlLowerCase.hasPrefix("http://") || fileUrlLowerCase.hasPrefix("https://") || fileUrlLowerCase.hasPrefix("file://") {
        vidURL = URL(string: fileUrl)
      } else {
        // Consider it's file url path
        vidURL = URL(fileURLWithPath: fileUrl)
      }

      guard let vidURL = vidURL else {
        reject("CreateVideoThumbnail", "Unable to create a URL from the provided video path", nil)
        return
      }
      let quality = Self.normalizedQuality(options["quality"])
      let asset = AVURLAsset(url: vidURL, options: ["AVURLAssetHTTPHeaderFieldsKey": headers])
      generateThumbImage(asset: asset, atTime: 0, completion: { thumbnail in
        // Generate thumbnail
        guard let data = thumbnail.jpegData(compressionQuality: quality) else {
          reject("CreateVideoThumbnail", "Unable to encode video thumbnail", nil)
          return
        }
        try? data.write(to: URL(fileURLWithPath: fullPath))
        resolve([
          "path": fullPath,
          "size": Float(data.count),
          "mime": "image/\(format)",
          "width": Float(thumbnail.size.width),
          "height": Float(thumbnail.size.height)
        ] as [String : Any])
      }, failure: { error in
          reject(error._domain, error.localizedDescription, nil)
      })

    } catch {
      reject("Error", error.localizedDescription, nil)
    }
  }

    static func cleanCacheDir(cacheDir: String, resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        let cachePathDir: String = cacheDir.isEmpty ? "thumbnails/" : cacheDir
        var cacheDirectoryPath = NSSearchPathForDirectoriesInDomains(.cachesDirectory, .userDomainMask, true).last ?? ""
        cacheDirectoryPath = (cacheDirectoryPath as NSString).appendingPathComponent(cachePathDir)

        let fm = FileManager.default

        if !fm.fileExists(atPath: cacheDirectoryPath) {
            reject("Error", "Directory does not exist", nil)
            return
        }

        do {
            let files = try fm.contentsOfDirectory(atPath: cacheDirectoryPath)
            for file in files {
                let filePath = (cacheDirectoryPath as NSString).appendingPathComponent(file)
                try fm.removeItem(atPath: filePath)
            }
            resolve("done")
        } catch {
            // Handle error if necessary
            reject("Error", error.localizedDescription, nil)
        }
    }

  private static func normalizedQuality(_ value: Any?) -> CGFloat {
    let rawValue = (value as? NSNumber)?.doubleValue ?? defaultQuality
    return CGFloat(min(max(rawValue, 0), 1))
  }

  func generateThumbImage(asset: AVURLAsset, atTime timeStamp: Int, completion: @escaping (UIImage) -> Void, failure: @escaping (Error) -> Void) {
    let generator = AVAssetImageGenerator(asset: asset)
    generator.appliesPreferredTrackTransform = true
    generator.maximumSize = CGSize(width: 512, height: 512)
    generator.requestedTimeToleranceBefore = .positiveInfinity
    generator.requestedTimeToleranceAfter = .positiveInfinity
    let times = [
      CMTimeMake(value: Int64(timeStamp), timescale: 1000),
      CMTimeMake(value: 1000, timescale: 1000)
    ]
    var lastError: Error?

    for time in times {
      do {
        let cgImage = try generator.copyCGImage(at: time, actualTime: nil)
        completion(UIImage(cgImage: cgImage))
        return
      } catch {
        lastError = error
      }
    }

    failure(lastError ?? NSError(domain: "CreateVideoThumbnail", code: -1, userInfo: [NSLocalizedDescriptionKey: "Unable to create thumbnail"]))
  }
}
