import CoreGraphics
import Photos
import Foundation
import ImageIO
import MobileCoreServices


// Local stand-in for the single `RCTResizeMode` case this file relies on, so the
// module no longer needs to `import React` (see HybridCompressor for the reason).
private enum CompressorResizeMode {
    case contain
}


class ImageCompressor {
    static func isCompressedSizeLessThanActualFile(sourceFileUrl: String?, sourceData: Data?, compressedFileUrl: String)-> Bool {
        var sourcefileSize: Int = 0
        
        if let _sourceFileUrl = sourceFileUrl {
            sourcefileSize = Utils.getfileSizeInBytes(forURL: ImageCompressor.fileURL(_sourceFileUrl))
        }
        
        if let _sourceData = sourceData {
            sourcefileSize = _sourceData.count
        }
        
        let compressedFileURL = URL(string: compressedFileUrl)
        let compressedfileSize = Utils.getfileSizeInBytes(forURL: compressedFileURL!)
        
        if compressedfileSize <= sourcefileSize {
            return true
        }
        
        return false
    }
    
    static func isPNG(_ data: Data) -> Bool {
        return data.starts(with: [0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A])
    }
    
    static func copyExifInfo(actualImagePath: String, image: UIImage, data: Data) -> Data {
        let source = CGImageSourceCreateWithURL(ImageCompressor.fileURL(actualImagePath) as CFURL, nil)!
        let metadata = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any]
        
        let dataProvider = CGDataProvider(data: data as CFData)
        let dataImageSource = CGImageSourceCreateWithDataProvider(dataProvider!, nil)!
        var dataMetadata = CGImageSourceCopyPropertiesAtIndex(dataImageSource, 0, nil) as? [CFString: Any]
        
        // Copy all keys from source metadata to destination metadata if they don't exist
        for (key, value) in metadata ?? [:] {
            if dataMetadata?[key] == nil {
                dataMetadata?[key] = value
            } else {
                if let metadataObj = dataMetadata?[key] as? NSMutableDictionary,
                    let valueObj = value as? NSDictionary {
                    for (key, value) in valueObj {
                        if metadataObj[key] == nil {
                            metadataObj[key] = value
                        }
                    }
                }
            }
        }
        
        // Pixel data here has already been decoded/rotated to the "up"
        // orientation (see decodeDownsampledImage below). Without forcing this
        // tag, the merge loop above copies the ORIGINAL file's Orientation tag
        // (commonly 3/6/8 for camera photos) onto an already-upright image —
        // any viewer that respects EXIF orientation will rotate it a second
        // time, incorrectly.
        dataMetadata?[kCGImagePropertyOrientation] = 1

        let outputFormat = isPNG(data) ? kUTTypePNG : kUTTypeJPEG

        let destinationData = NSMutableData()
        let destination = CGImageDestinationCreateWithData(destinationData, outputFormat, 1, nil)!
        CGImageDestinationAddImage(destination, image.cgImage!, dataMetadata as CFDictionary?)
        CGImageDestinationFinalize(destination)
        return destinationData as Data
    }
    
    static func writeImage(_ image: UIImage, output: Int, quality: Float, outputExtension: String, isBase64: Bool, disablePngTransparency: Bool, isEnableAutoCompress: Bool, actualImagePath: String?)-> String {
        var data: Data
        var exception: NSException?
        let normalizedQuality = CGFloat(min(max(quality, 0), 1))
        
        switch OutputType(rawValue: output)! {
        case .jpg:
            data = image.jpegData(compressionQuality: normalizedQuality)!
        case .png:
            if disablePngTransparency {
                data = image.jpegData(compressionQuality: normalizedQuality)!
                let compressedImage = UIImage(data: data)
                data = compressedImage!.pngData()!
            } else {
                data = image.pngData()!
            }
        }
        
        if let _actualImagePath = actualImagePath {
            data = copyExifInfo(actualImagePath: _actualImagePath, image: UIImage(data: data) ?? image, data: data)
        }
        
        if isBase64 {
            return data.base64EncodedString(options: [])
        } else {
            let filePath = Utils.generateCacheFilePath(outputExtension)
            
            do {
                try data.write(to: URL(fileURLWithPath: filePath), options: .atomic)
                let returnablePath = makeValidUri(filePath)
                
                if isEnableAutoCompress == true {
                    if self.isCompressedSizeLessThanActualFile(sourceFileUrl: actualImagePath, sourceData: data, compressedFileUrl: returnablePath) {
                        return returnablePath
                    } else {
                        MediaCache.deleteFile(atPath: returnablePath)
                        
                        if let _actualImagePath = actualImagePath {
                            return _actualImagePath
                        }
                    }
                }
                
                return returnablePath
                
            } catch {
                exception = NSException(name: NSExceptionName(rawValue: "file_error"), reason: "Error writing file", userInfo: nil)
                exception?.raise()
            }
        }
        return ""
    }

    
    static func manualCompress(_ image: UIImage, output: Int, quality: Float, outputExtension: String, isBase64: Bool, disablePngTransparency: Bool, actualImagePath: String?) -> String {
        return writeImage(image, output: output, quality: quality, outputExtension: outputExtension, isBase64: isBase64, disablePngTransparency: disablePngTransparency, isEnableAutoCompress: false, actualImagePath: actualImagePath)
    }

    
    static func makeValidUri(_ filePath: String) -> String {
        let fileWithUrl = URL(fileURLWithPath: filePath)
        let absoluteUrl = fileWithUrl.deletingLastPathComponent()
        let fileUrl = "file://\(absoluteUrl.path)/\(fileWithUrl.lastPathComponent)"
        return fileUrl
    }

    /// Builds a file URL from a file: URI or a bare absolute path. URL(string:)
    /// returns nil on iOS < 17 for a URI that isn't percent-encoded (e.g. it
    /// contains spaces), so fall back to the path with its scheme stripped.
    static func fileURL(_ path: String) -> URL {
        guard path.hasPrefix("file:") else { return URL(fileURLWithPath: path) }
        if let url = URL(string: path) { return url }
        return URL(fileURLWithPath: path.replacingOccurrences(of: "^file:/*", with: "/", options: .regularExpression))
    }

    /// Opens a CGImageSource for the given path — file:, data: or a bare
    /// absolute path, WITHOUT decoding the image. This lets ImageIO downsample directly from the
    /// source when generating a thumbnail, instead of us decoding the full
    /// image into memory first.
    static func makeImageSource(_ path: String) -> CGImageSource? {
        if path.hasPrefix("data:") {
            guard let dataURL = URL(string: path), let data = try? Data(contentsOf: dataURL) else { return nil }
            return CGImageSourceCreateWithData(data as CFData, nil)
        }
        return CGImageSourceCreateWithURL(ImageCompressor.fileURL(path) as CFURL, nil)
    }

    /// Decodes the image already downsampled AND orientation-corrected in a
    /// single step via CGImageSourceCreateThumbnailAtIndex, instead of
    /// UIImage(contentsOfFile:) followed by redrawing the FULL-resolution
    /// bitmap into a new CGContext (the old path: peaks at ~2x the full
    /// native-resolution RGBA buffer in memory, which is the root cause of
    /// OOM crashes on lower-RAM devices when compressing large/HEIC photos).
    /// kCGImageSourceCreateThumbnailWithTransform: true makes ImageIO apply
    /// the EXIF orientation transform as part of decoding, so the returned
    /// image is always orientation .up.
    static func decodeDownsampledImage(imagePath: String?, base64: String?, input: InputType, maxWidth: Int, maxHeight: Int) -> UIImage? {
        let imageSource: CGImageSource?

        switch input {
        case .base64:
            guard let base64Value = base64,
                  let data = Data(base64Encoded: base64Value, options: .ignoreUnknownCharacters) else {
                return nil
            }
            imageSource = CGImageSourceCreateWithData(data as CFData, nil)
        case .uri:
            guard let path = imagePath else { return nil }
            imageSource = ImageCompressor.makeImageSource(path)
        }

        guard let source = imageSource else { return nil }

        // kCGImageSourceThumbnailMaxPixelSize takes a single value for the longer
        // edge, so derive it from the native size (metadata only, no decoding):
        // fit inside both maxWidth and maxHeight, and never upscale.
        var targetLongEdge = min(maxWidth, maxHeight)
        if let properties = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any],
           let pixelWidth = properties[kCGImagePropertyPixelWidth] as? Int,
           let pixelHeight = properties[kCGImagePropertyPixelHeight] as? Int {
            // EXIF orientations 5-8 swap width and height once applied.
            let isTransposed = (properties[kCGImagePropertyOrientation] as? Int ?? 1) >= 5
            let width = CGFloat(isTransposed ? pixelHeight : pixelWidth)
            let height = CGFloat(isTransposed ? pixelWidth : pixelHeight)
            let scale = min(1, CGFloat(maxWidth) / width, CGFloat(maxHeight) / height)
            targetLongEdge = max(1, Int((max(width, height) * scale).rounded()))
        }

        let thumbnailOptions: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceThumbnailMaxPixelSize: targetLongEdge,
            kCGImageSourceShouldCacheImmediately: true,
        ]

        guard let thumbnail = CGImageSourceCreateThumbnailAtIndex(source, 0, thumbnailOptions as CFDictionary) else {
            return nil
        }

        return UIImage(cgImage: thumbnail)
    }

    static func manualCompressHandler(imagePath: String?, base64: String?, options: ImageCompressorOptions) -> String {
        var exception: NSException?

        if let image = ImageCompressor.decodeDownsampledImage(imagePath: imagePath, base64: base64, input: options.input, maxWidth: options.maxWidth, maxHeight: options.maxHeight) {
            let outputExtension = ImageCompressorOptions.getOutputInString(options.output)
            let isBase64 = options.returnableOutputType == .rbase64
            return ImageCompressor.manualCompress(image, output: options.output.rawValue, quality: options.quality, outputExtension: outputExtension, isBase64: isBase64, disablePngTransparency: options.disablePngTransparency, actualImagePath: imagePath)
        } else {
            exception = NSException(name: NSExceptionName(rawValue: "unsupported_value"), reason: "Unsupported value type.", userInfo: nil)
            exception?.raise()
        }

        return ""
    }


    static func autoCompressHandler(imagePath: String?, base64: String?, options: ImageCompressorOptions) -> String {
        var exception: NSException?

        if let image = ImageCompressor.decodeDownsampledImage(imagePath: imagePath, base64: base64, input: options.input, maxWidth: options.maxWidth, maxHeight: options.maxHeight) {
            let outputExtension = ImageCompressorOptions.getOutputInString(options.output)
            let isBase64 = options.returnableOutputType == .rbase64
            return ImageCompressor.writeImage(image, output: options.output.rawValue, quality: options.quality, outputExtension: outputExtension, isBase64: isBase64, disablePngTransparency: options.disablePngTransparency, isEnableAutoCompress: true, actualImagePath: imagePath)
        } else {
            exception = NSException(name: NSExceptionName(rawValue: "unsupported_value"), reason: "Unsupported value type.", userInfo: nil)
            exception?.raise()
        }

        return ""
    }
    
    static func getAbsoluteImagePath(_ imagePath: String, options: ImageCompressorOptions, completionHandler: @escaping (String) -> Void) {
            if imagePath.hasPrefix("http://") || imagePath.hasPrefix("https://") {
                let uuid=options.uuid ?? ""
                let progressDivider=options.progressDivider
                Downloader.downloadFileAndSaveToCache(imagePath, uuid: uuid,progressDivider: progressDivider) { downloadedPath in
                    completionHandler(downloadedPath)
                }
                return
            } else if !imagePath.contains("ph://") {
                completionHandler(Utils.slashifyFilePath(path: imagePath)!)
                return
            }
            
            let imageURL = URL(string: imagePath.replacingOccurrences(of: " ", with: "%20"))
            var size = CGSize.zero
            var scale: CGFloat = 1
            var resizeMode = CompressorResizeMode.contain
            let assetID = imagePath.replacingOccurrences(of: "ph://", with: "")
            let results = PHAsset.fetchAssets(withLocalIdentifiers: [assetID], options: nil)
            
            guard let asset = results.firstObject else {
                return
            }
            
            let imageOptions = PHImageRequestOptions()
            imageOptions.isNetworkAccessAllowed = true
            imageOptions.deliveryMode = .highQualityFormat
            
            var useMaximumSize = size.equalTo(CGSize.zero)
            var targetSize: CGSize
            
            if useMaximumSize {
                targetSize = PHImageManagerMaximumSize
                imageOptions.resizeMode = .none
            } else {
                targetSize = CGSize(width: size.width * scale, height: size.height * scale)
                imageOptions.resizeMode = .fast
            }
            
            var contentMode = PHImageContentMode.aspectFill
            if resizeMode == .contain {
                contentMode = .aspectFit
            }
            
            PHImageManager.default().requestImage(for: asset, targetSize: targetSize, contentMode: contentMode, options: imageOptions) { result, info in
                if let result = result {
                    let imageName = assetID.replacingOccurrences(of: "/", with: "_")
                    if let imagePath = saveImageIntoCache(result, withName: imageName) {
                        completionHandler(imagePath)
                    }
                }
            }
        }
    
    static func saveImageIntoCache(_ image: UIImage, withName name: String) -> String? {
            if let imageData = image.jpegData(compressionQuality: 1),
               let filePath: String? = Utils.generateCacheFilePath("jpg") {
                do {
                    try imageData.write(to: URL(fileURLWithPath: filePath!), options: .atomic)
                    return filePath
                } catch {
                    return nil
                }
            }
            return nil
        }
}
