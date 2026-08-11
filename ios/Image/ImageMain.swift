//
//  ImageMain.swift
//  react-native-compressor
//
//  Created by Numan on 10/09/2023.
//

import Foundation
import ImageIO
class ImageMain {
    static func image_compress(_ value: String, optionMap: NSDictionary, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        do {
            let options = ImageCompressorOptions.fromDictionary(optionMap as! [String : Any])
            
            if options.input != InputType.base64 {
                ImageCompressor.getAbsoluteImagePath(value, options: options) { absoluteImagePath in
                    if options.autoCompress {
                        let result = ImageCompressor.autoCompressHandler(imagePath: absoluteImagePath, base64: nil, options: options)
                        resolve(result)
                    } else {
                        let result = ImageCompressor.manualCompressHandler(imagePath: absoluteImagePath, base64: nil, options: options)
                        resolve(result)
                    }
                    MediaCache.removeCompletedImagePath(absoluteImagePath)
                }
            } else {
                if options.autoCompress {
                    let result = ImageCompressor.autoCompressHandler(imagePath: nil, base64: value, options: options)
                    resolve(result)
                } else {
                    let result = ImageCompressor.manualCompressHandler(imagePath: nil, base64: value, options: options)
                    resolve(result)
                }
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
                
                let properties = imageProperties as NSDictionary
                var result: [String: Any] = [:]

                if let fileSizeString = fileSize as? String, let fileSizeInt = Int(fileSizeString) {
                    result["size"] = fileSizeInt
                }
                result["extension"] = _extension
                result["fileName"] = (absoluteImagePath as NSString).lastPathComponent
                result["mimeType"] = ImageMain.mimeType(for: imageSource, fallbackExtension: _extension)

                let width = properties[kCGImagePropertyPixelWidth] as? Int ?? 0
                let height = properties[kCGImagePropertyPixelHeight] as? Int ?? 0
                guard width > 0, height > 0 else {
                    reject("INVALID_IMAGE", "Failed to read image dimensions from \(absoluteImagePath)", nil)
                    return
                }
                result["width"] = width
                result["height"] = height
                result["hasAlpha"] = properties[kCGImagePropertyHasAlpha] as? Bool ?? false
                ImageMain.applyOrientation(properties[kCGImagePropertyOrientation] as? Int, width: width, height: height, into: &result)

                if let depth = properties[kCGImagePropertyDepth] as? Int { result["bitDepth"] = depth }
                if let profile = properties[kCGImagePropertyProfileName] as? String { result["colorSpace"] = profile }
                if let dpiX = properties[kCGImagePropertyDPIWidth] as? Double { result["dpiX"] = dpiX }
                if let dpiY = properties[kCGImagePropertyDPIHeight] as? Double { result["dpiY"] = dpiY }

                let frameCount = CGImageSourceGetCount(imageSource)
                result["frameCount"] = frameCount
                result["isAnimated"] = frameCount > 1

                if let tiff = properties[kCGImagePropertyTIFFDictionary] as? [CFString: Any] {
                    if let make = tiff[kCGImagePropertyTIFFMake] as? String { result["make"] = make }
                    if let model = tiff[kCGImagePropertyTIFFModel] as? String { result["model"] = model }
                    if let software = tiff[kCGImagePropertyTIFFSoftware] as? String { result["software"] = software }
                }

                if let exif = properties[kCGImagePropertyExifDictionary] as? [CFString: Any] {
                    if let lensModel = exif[kCGImagePropertyExifLensModel] as? String { result["lensModel"] = lensModel }
                    if let iso = (exif[kCGImagePropertyExifISOSpeedRatings] as? [Int])?.first { result["iso"] = iso }
                    if let exposureTime = exif[kCGImagePropertyExifExposureTime] as? Double { result["exposureTime"] = exposureTime }
                    if let focalLength = exif[kCGImagePropertyExifFocalLength] as? Double { result["focalLength"] = focalLength }
                    // Bit 0 of the flash tag is "flash fired"; the remaining bits describe the mode.
                    if let flash = exif[kCGImagePropertyExifFlash] as? Int { result["flash"] = flash & 0x1 == 1 }
                }

                if let gps = properties[kCGImagePropertyGPSDictionary] as? [CFString: Any] {
                    ImageMain.applyGPS(gps, into: &result)
                }

                resolve(result)
            } reject: { code, message, error in
                reject(code, message, error)
            }
        }
    }

    /// Maps the type ImageIO detected onto a canonical mime type. Deliberately not routed through
    /// `UTTypeCopyPreferredTagWithClass` (deprecated in iOS 15, and `UTType` needs iOS 14 while the
    /// deployment target is 13.4) — and the explicit table also keeps these values identical to the
    /// ones Android reports, which the preferred-tag lookup does not (`image/heic` vs `image/heif`).
    private static func mimeType(for imageSource: CGImageSource, fallbackExtension: String) -> String {
        guard let uti = CGImageSourceGetType(imageSource) as String? else {
            return mimeType(forExtension: fallbackExtension)
        }
        switch uti {
        case "public.jpeg": return "image/jpeg"
        case "public.png": return "image/png"
        case "com.compuserve.gif": return "image/gif"
        case "org.webmproject.webp", "public.webp": return "image/webp"
        case "public.heic", "public.heif", "public.heics", "public.avci": return "image/heif"
        case "public.tiff": return "image/tiff"
        case "com.microsoft.bmp": return "image/bmp"
        case "com.adobe.raw-image": return "image/x-adobe-dng"
        default: return mimeType(forExtension: fallbackExtension)
        }
    }

    private static func mimeType(forExtension fileExtension: String) -> String {
        switch fileExtension.lowercased() {
        case "jpg", "jpeg": return "image/jpeg"
        case "png": return "image/png"
        case "gif": return "image/gif"
        case "webp": return "image/webp"
        case "heic", "heif": return "image/heif"
        case "bmp": return "image/bmp"
        case "tif", "tiff": return "image/tiff"
        case "dng": return "image/x-adobe-dng"
        default: return "image/\(fileExtension.lowercased())"
        }
    }

    /// Maps the raw EXIF orientation 1-8 onto `rotation`, `isMirrored` and the display dimensions.
    private static func applyOrientation(_ rawOrientation: Int?, width: Int, height: Int, into result: inout [String: Any]) {
        // Orientation 0 is not a legal EXIF value; treat it as "unrotated" like a missing tag.
        let orientation = (rawOrientation.map { (1...8).contains($0) ? $0 : 1 }) ?? 1
        let rotation: Int
        switch orientation {
        case 3, 4: rotation = 180
        case 5, 6: rotation = 90
        case 7, 8: rotation = 270
        default: rotation = 0
        }
        // Orientations 5-8 are the quarter turns, and only those swap the display axes.
        let swapsAxes = orientation >= 5
        result["orientation"] = orientation
        result["rotation"] = rotation
        result["isMirrored"] = orientation == 2 || orientation == 4 || orientation == 5 || orientation == 7
        result["displayWidth"] = swapsAxes ? height : width
        result["displayHeight"] = swapsAxes ? width : height
    }

    /// Converts the GPS dictionary's unsigned magnitudes into signed decimal degrees. Ignoring the
    /// reference letters would place southern and western coordinates in the wrong hemisphere.
    private static func applyGPS(_ gps: [CFString: Any], into result: inout [String: Any]) {
        if let latitude = gps[kCGImagePropertyGPSLatitude] as? Double {
            let isSouth = (gps[kCGImagePropertyGPSLatitudeRef] as? String)?.uppercased() == "S"
            result["latitude"] = isSouth ? -latitude : latitude
        }
        if let longitude = gps[kCGImagePropertyGPSLongitude] as? Double {
            let isWest = (gps[kCGImagePropertyGPSLongitudeRef] as? String)?.uppercased() == "W"
            result["longitude"] = isWest ? -longitude : longitude
        }
        if let altitude = gps[kCGImagePropertyGPSAltitude] as? Double {
            // AltitudeRef 1 means "below sea level".
            let isBelowSeaLevel = (gps[kCGImagePropertyGPSAltitudeRef] as? Int) == 1
            result["altitude"] = isBelowSeaLevel ? -altitude : altitude
        }
    }
}
