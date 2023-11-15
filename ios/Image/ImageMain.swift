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
            if ImagePath.hasPrefix("file://") {
                let absoluteImagePath = URL(string: ImagePath)!.path
                Utils.getFileSize(absoluteImagePath) { fileSize in
                    let _extension = (absoluteImagePath as NSString).pathExtension
                    let imageData = NSData(contentsOfFile: absoluteImagePath)!
                    let imageSource = CGImageSourceCreateWithData(imageData, nil)!
                    let imageProperties = CGImageSourceCopyPropertiesAtIndex(imageSource, 0, nil)!
                    let exif=imageProperties as NSDictionary
                    let mutableExif = exif.mutableCopy() as! NSMutableDictionary
                    if let fileSizeInt = Int(fileSize as! String) {
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
}
