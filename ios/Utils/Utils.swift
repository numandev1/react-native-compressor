//
//  Utils.swift
//  react-native-compressor
//
//  Created by Numan on 10/09/2023.
//

import Foundation

class Utils {
    static func generateCacheFilePath(_ extension: String) -> String {
        let uuid = UUID()
        let imageNameWithoutExtension = uuid.uuidString
        let imageName = imageNameWithoutExtension + "." + `extension`
        let filePath = (NSTemporaryDirectory() as NSString).appendingPathComponent(imageName)
        return filePath
    }
    
    static func makeValidUri(filePath: String) -> String {
        let fileWithUrl = URL(fileURLWithPath: filePath)
        let absoluteUrl = fileWithUrl.deletingLastPathComponent()
        let fileUrl = "file://\(absoluteUrl.path)/\(fileWithUrl.lastPathComponent)"
        return fileUrl;
    }
    
    static func getFileSize(from urlString: String, completion: @escaping (NSNumber?, Error?) -> Void) {
        if let url = URL(string: urlString) {
            var request = URLRequest(url: url)
            request.httpMethod = "HEAD"

            let session = URLSession.shared
            let task = session.dataTask(with: request) { data, response, error in
                if let error = error {
                    completion(nil, error)
                } else if let httpResponse = response as? HTTPURLResponse {
                    let contentLength = NSNumber(value: httpResponse.expectedContentLength)
                    completion(contentLength, nil)
                } else {
                    let error = NSError(domain: "FileDownloadError", code: -1, userInfo: [NSLocalizedDescriptionKey: "Invalid response."])
                    completion(nil, error)
                }
            }

            task.resume()
        }
    }
    
    static func getFileSize(_ filePath: String, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        do {
            if filePath.hasPrefix("http://") || filePath.hasPrefix("https://") {
                getFileSize(from: filePath) { fileSize, error in
                    if let error = error {
                        print("Error: \(error.localizedDescription)")
                        reject(String(error._code), error.localizedDescription, error)
                    } else if let fileSize = fileSize {
                        print("File size: \(fileSize)")
                        resolve(fileSize.stringValue)
                    }
                }
            } else {
                var filePath = filePath
                if filePath.hasPrefix("file://") {
                    filePath = filePath.replacingOccurrences(of: "file://", with: "")
                }
                let fileManager = FileManager.default
                var isDir: ObjCBool = false
                if !fileManager.fileExists(atPath: filePath, isDirectory: &isDir) || isDir.boolValue {
                    let err = NSError(domain: "file not found", code: -15, userInfo: nil)
                    reject(String(err.code), err.localizedDescription, err)
                    return
                }
                if let attrs = try? fileManager.attributesOfItem(atPath: filePath),
                   let fileSize = attrs[.size] as? UInt64 {
                    let fileSizeString = String(fileSize)
                    resolve(fileSizeString)
                }
            }
        } catch {
            reject(error.localizedDescription, error.localizedDescription, nil)
        }
    }
    
    
    static func getRealPath(_ path: String, type: String, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        do {
            if type == "video" {
                VideoCompressor.getAbsoluteVideoPath(path, options: [:]) { absoluteImagePath in
                    resolve(absoluteImagePath)
                }
            } else {
                let options = ImageCompressorOptions.fromDictionary([:])
                ImageCompressor.getAbsoluteImagePath(path, options: options) { absoluteImagePath in
                    resolve(absoluteImagePath)
                }
            }
        } catch {
            reject(error.localizedDescription, error.localizedDescription, nil)
        }
    }
    
    static func generateFilePath(_ _extension: String, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        do {
            let outputUri = generateCacheFilePath(_extension)
            resolve(outputUri)
        } catch {
            reject(error.localizedDescription, error.localizedDescription, nil)
        }
    }
    
}
