//
//  ImageMain.swift
//  react-native-compressor
//
//  Created by Numan on 10/09/2023.
//

import Foundation
class ImageMain {
    static func image_compress(_ imagePath: String, optionMap: NSDictionary, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        do {
            let options = ImageCompressorOptions.fromDictionary(optionMap as! [String : Any])
            ImageCompressor.getAbsoluteImagePath(imagePath, options: options) { absoluteImagePath in
                if options.autoCompress {
                    let result = ImageCompressor.autoCompressHandler(absoluteImagePath, options: options)
                    resolve(result)
                } else {
                    let result = ImageCompressor.manualCompressHandler(absoluteImagePath, options: options)
                    resolve(result)
                }
                MediaCache.removeCompletedImagePath(absoluteImagePath)
            }
        } catch {
            reject(error.localizedDescription, error.localizedDescription, nil)
        }
    }
    
    static func getImageMetaData(_ filePath: String, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        let options = ImageCompressorOptions.fromDictionary([:])
        ImageCompressor.getAbsoluteImagePath(filePath, options: options) { ImagePath in
            guard ImagePath.hasPrefix("file://") else {
                reject("INVALID_PATH", "Image path must start with file://", nil)
                return
            }
            
            guard let url = URL(string: ImagePath) else {
                reject("INVALID_URL", "Failed to create URL from image path", nil)
                return
            }
            
            let absoluteImagePath = url.path
            let fileURL = url as CFURL
            
            Utils.getFileSize(absoluteImagePath) { fileSize in
                let _extension = (absoluteImagePath as NSString).pathExtension
                
                // Use CGImageSourceCreateWithURL to avoid loading entire file into memory (prevents OOM crashes for large images)
                guard let imageSource = CGImageSourceCreateWithURL(fileURL, nil) else {
                    reject("INVALID_IMAGE", "Failed to create image source from URL", nil)
                    return
                }
                
                guard let imageProperties = CGImageSourceCopyPropertiesAtIndex(imageSource, 0, nil) else {
                    reject("NO_PROPERTIES", "Failed to get image properties", nil)
                    return
                }
                
                let exif = imageProperties as NSDictionary
                guard let mutableExif = exif.mutableCopy() as? NSMutableDictionary else {
                    reject("COPY_ERROR", "Failed to create mutable copy of image properties", nil)
                    return
                }
                
                if let fileSizeString = fileSize as? String, let fileSizeInt = Int(fileSizeString) {
                    mutableExif.setValue(fileSizeInt, forKey: "size")
                }
                
                mutableExif.setValue(_extension, forKey: "extension")
                resolve(mutableExif)
            } reject: { code, message, error in
                reject(code, message, error)
            }
        }
    }
}
