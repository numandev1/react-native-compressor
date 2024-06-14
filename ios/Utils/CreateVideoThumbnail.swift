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

      let asset = AVURLAsset(url: vidURL!, options: ["AVURLAssetHTTPHeaderFieldsKey": headers])
      generateThumbImage(asset: asset, atTime: 0, completion: { thumbnail in
        // Generate thumbnail
        var data: Data? = thumbnail.jpegData(compressionQuality: 1.0)

        if let data = data {
          try? data.write(to: URL(fileURLWithPath: fullPath))
          resolve([
            "path": fullPath,
            "size": Float(data.count),
            "mime": "image/\(format)",
            "width": Float(thumbnail.size.width),
            "height": Float(thumbnail.size.height)
          ] as [String : Any])
        }
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

  func generateThumbImage(asset: AVURLAsset, atTime timeStamp: Int, completion: @escaping (UIImage) -> Void, failure: @escaping (Error) -> Void) {
    let generator = AVAssetImageGenerator(asset: asset)
    generator.appliesPreferredTrackTransform = true
    generator.maximumSize = CGSize(width: 512, height: 512)
    generator.requestedTimeToleranceBefore = CMTimeMake(value: 0, timescale: 1000)
    generator.requestedTimeToleranceAfter = CMTimeMake(value: 0, timescale: 1000)
    let time = CMTimeMake(value: Int64(timeStamp), timescale: 1000)
    generator.generateCGImagesAsynchronously(forTimes: [NSValue(time: time)]) { _, image, _, result, error in
      if result == .succeeded, let cgImage = image {
        let thumbnail = UIImage(cgImage: cgImage)
        completion(thumbnail)
      } else if let error = error {
        failure(error)
      }
    }
  }
}
