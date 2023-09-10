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
}
