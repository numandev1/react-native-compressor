import Accelerate
import CoreGraphics
import Photos
import React
import Foundation
import MobileCoreServices


class ImageCompressor {
    static func findTargetSize(_ image: UIImage, maxWidth: Int, maxHeight: Int) -> CGSize {
        let width = image.size.width
        let height = image.size.height

        if width > height {
            let newHeight = height / (width / CGFloat(maxWidth))
            return CGSize(width: CGFloat(maxWidth), height: newHeight)
        }

        let newWidth = width / (height / CGFloat(maxHeight))
        return CGSize(width: newWidth, height: CGFloat(maxHeight))
    }

    
    static func decodeImage(_ value: String) -> UIImage? {
        if let data = Data(base64Encoded: value, options: .ignoreUnknownCharacters) {
            return UIImage(data: data)
        }
        return nil
    }

    
    static func loadImage(_ path: String) -> UIImage? {
        var image: UIImage?

        if path.hasPrefix("data:") || path.hasPrefix("file:") {
            if let imageUrl = URL(string: path), let imageData = try? Data(contentsOf: imageUrl) {
                image = UIImage(data: imageData)
            }
        } else {
            image = UIImage(contentsOfFile: path)
        }

        return image
    }

    
    static func manualResize(_ image: UIImage, maxWidth: Int, maxHeight: Int) -> UIImage {
        let targetSize = findTargetSize(image, maxWidth: maxWidth, maxHeight: maxHeight)

        if let cgImage = image.cgImage {
            let sourceWidth = Int(image.size.width)
            let sourceHeight = Int(image.size.height)
            let targetWidth = Int(targetSize.width)
            let targetHeight = Int(targetSize.height)
            let bytesPerPixel = 4
            let sourceBytesPerRow = sourceWidth * bytesPerPixel
            let targetBytesPerRow = targetWidth * bytesPerPixel
            let bitsPerComponent = 8

            let colorSpace = CGColorSpaceCreateDeviceRGB()

            var sourceData = [UInt8](repeating: 0, count: sourceHeight * sourceBytesPerRow)
            let context = CGContext(data: &sourceData,
                                    width: Int(sourceWidth),
                                    height: Int(sourceHeight),
                                    bitsPerComponent: bitsPerComponent,
                                    bytesPerRow: sourceBytesPerRow,
                                    space: colorSpace,
                                    bitmapInfo: CGImageAlphaInfo.premultipliedFirst.rawValue | CGBitmapInfo.byteOrder32Big.rawValue)

            context?.draw(cgImage, in: CGRect(x: 0, y: 0, width: sourceWidth, height: sourceHeight))
         

            var targetData = [UInt8](repeating: 0, count: targetWidth * targetHeight * bytesPerPixel)

            var srcBuffer = vImage_Buffer(data: &sourceData, height: vImagePixelCount(sourceHeight), width: vImagePixelCount(sourceWidth), rowBytes: sourceBytesPerRow)
            var targetBuffer = vImage_Buffer(data: &targetData, height: vImagePixelCount(targetHeight), width: vImagePixelCount(targetWidth), rowBytes: targetBytesPerRow)

            let error = vImageScale_ARGB8888(&srcBuffer, &targetBuffer, nil, vImage_Flags(kvImageHighQualityResampling))

            if error != kvImageNoError {
                free(&targetData)
                let exception = NSException(name: NSExceptionName(rawValue: "drawing_error"), reason: "Problem while rendering your image", userInfo: nil)
                exception.raise()
            }

            let targetContext = CGContext(data: &targetData,
                                          width: targetWidth,
                                          height: targetHeight,
                                          bitsPerComponent: bitsPerComponent,
                                          bytesPerRow: targetBytesPerRow,
                                          space: colorSpace,
                                          bitmapInfo: CGImageAlphaInfo.premultipliedFirst.rawValue | CGBitmapInfo.byteOrder32Big.rawValue)

            let targetRef = targetContext?.makeImage()
            let resizedImage = UIImage(cgImage: targetRef!)
            

            return resizedImage
        }
        return UIImage()
    }
    
    static func isCompressedSizeLessThanActualFile(sourceFileUrl: String,compressedFileUrl: String)-> Bool {
        let sourceVideoURL = URL(string: sourceFileUrl)
        let sourcefileSize:Double=Utils.getfileSizeInBytes(forURL:sourceVideoURL!)
        
        let compressedVideoURL = URL(string: compressedFileUrl)
        let compressedfileSize:Double=Utils.getfileSizeInBytes(forURL:compressedVideoURL!)
        
        if(compressedfileSize<=sourcefileSize)
        {
        return true
        }
        return false
    }
    
    static func isPNG(_ data: Data) -> Bool {
        return data.starts(with: [0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A])
    }
    
    static func copyExifInfo(actualImagePath:String, image: UIImage, data: Data) -> Data {
        let fileURL = URL(string: actualImagePath)!
        let filePath = fileURL.path
        
        let url = URL(fileURLWithPath: filePath)
        let source = CGImageSourceCreateWithURL(url as CFURL, nil)!
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
        
        let outputFormat = isPNG(data) ? kUTTypePNG : kUTTypeJPEG
        
        let destinationData = NSMutableData()
        let destination = CGImageDestinationCreateWithData(destinationData, outputFormat, 1, nil)!
        CGImageDestinationAddImage(destination, image.cgImage!, dataMetadata as CFDictionary?)
        CGImageDestinationFinalize(destination)
        return destinationData as Data
    }
    
    static func writeImage(_ image: UIImage, output: Int, quality: Float, outputExtension: String, isBase64: Bool,disablePngTransparency:Bool,isEnableAutoCompress:Bool,actualImagePath:String)-> String{
        var data: Data
        var exception: NSException?
        switch OutputType(rawValue: output)! {
        case .jpg:
            data = image.jpegData(compressionQuality: CGFloat(quality))!
        case .png:
            if(disablePngTransparency)
            {
                data = image.jpegData(compressionQuality: CGFloat(quality))!
                let compressedImage = UIImage(data: data)
                data = compressedImage!.pngData()!
            }
            else
            {
                data=image.pngData()!
            }
           
        }
        
        data=copyExifInfo(actualImagePath: actualImagePath, image: UIImage(data: data) ?? image, data: data)
        
        
        if isBase64 {
            return data.base64EncodedString(options: [])
        } else {
            let filePath = Utils.generateCacheFilePath(outputExtension)
            do {
                try data.write(to: URL(fileURLWithPath: filePath), options: .atomic)
                let returnablePath = makeValidUri(filePath)
                if(isEnableAutoCompress==true)
                {
                    if(self.isCompressedSizeLessThanActualFile(sourceFileUrl: actualImagePath,compressedFileUrl: returnablePath))
                    {
                        return returnablePath
                    }
                    else
                    {
                        MediaCache.deleteFile(atPath:returnablePath)
                        return actualImagePath
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

    
    static func manualCompress(_ image: UIImage, output: Int, quality: Float, outputExtension: String, isBase64: Bool,disablePngTransparency:Bool,actualImagePath:String) -> String {
        return writeImage(image, output: output, quality: quality, outputExtension: outputExtension, isBase64: isBase64, disablePngTransparency: disablePngTransparency,isEnableAutoCompress: false,actualImagePath: actualImagePath)
    }

    
    static func scaleAndRotateImage(_ image: UIImage) -> UIImage {
        if image.imageOrientation == .up {
            return image
        }

        var transform = CGAffineTransform.identity

        switch image.imageOrientation {
        case .down, .downMirrored:
            transform = transform.translatedBy(x: image.size.width, y: image.size.height)
            transform = transform.rotated(by: CGFloat.pi)
        case .left, .leftMirrored:
            transform = transform.translatedBy(x: image.size.width, y: 0)
            transform = transform.rotated(by: CGFloat.pi / 2)
        case .right, .rightMirrored:
            transform = transform.translatedBy(x: 0, y: image.size.height)
            transform = transform.rotated(by: -CGFloat.pi / 2)
        default:
            break
        }

        switch image.imageOrientation {
        case .upMirrored, .downMirrored:
            transform = transform.translatedBy(x: image.size.width, y: 0)
            transform = transform.scaledBy(x: -1, y: 1)
        case .leftMirrored, .rightMirrored:
            transform = transform.translatedBy(x: image.size.height, y: 0)
            transform = transform.scaledBy(x: -1, y: 1)
        default:
            break
        }

        if let cgImage = image.cgImage, let colorSpace = cgImage.colorSpace {
            guard let context = CGContext(data: nil, width: Int(image.size.width), height: Int(image.size.height),
                                          bitsPerComponent: cgImage.bitsPerComponent, bytesPerRow: 0,
                                          space: colorSpace, bitmapInfo: cgImage.bitmapInfo.rawValue) else {
                return image
            }

            context.concatenate(transform)

            switch image.imageOrientation {
            case .left, .leftMirrored, .right, .rightMirrored:
                context.draw(cgImage, in: CGRect(x: 0, y: 0, width: image.size.height, height: image.size.width))
            default:
                context.draw(cgImage, in: CGRect(x: 0, y: 0, width: image.size.width, height: image.size.height))
            }

            if let rotatedCGImage = context.makeImage() {
                return UIImage(cgImage: rotatedCGImage)
            }
        }

        return image
    }

    
    static func makeValidUri(_ filePath: String) -> String {
        let fileWithUrl = URL(fileURLWithPath: filePath)
        let absoluteUrl = fileWithUrl.deletingLastPathComponent()
        let fileUrl = "file://\(absoluteUrl.path)/\(fileWithUrl.lastPathComponent)"
        return fileUrl
    }

    
  static func manualCompressHandler(_ imagePath: String, options: ImageCompressorOptions) -> String {
        var exception: NSException?
        var image: UIImage?

        switch options.input {
        case .base64:
            image = ImageCompressor.decodeImage(imagePath)
        case .uri:
            image = ImageCompressor.loadImage(imagePath)
        }

        if let _image = image {
            image = ImageCompressor.scaleAndRotateImage(_image)
            let outputExtension = ImageCompressorOptions.getOutputInString(options.output)
            let resizedImage = ImageCompressor.manualResize(image!, maxWidth: options.maxWidth, maxHeight: options.maxHeight)
            let isBase64 = options.returnableOutputType == .rbase64
            return ImageCompressor.manualCompress(resizedImage, output: options.output.rawValue, quality: options.quality, outputExtension: outputExtension, isBase64: isBase64,disablePngTransparency: options.disablePngTransparency,actualImagePath: imagePath)
        } else {
            exception = NSException(name: NSExceptionName(rawValue: "unsupported_value"), reason: "Unsupported value type.", userInfo: nil)
            exception?.raise()
        }

        return ""
    }

    
    static func autoCompressHandler(_ imagePath: String, options: ImageCompressorOptions) -> String {
        var exception: NSException?
        var image = ImageCompressor.loadImage(imagePath)
        
        if var image = image {
            image = ImageCompressor.scaleAndRotateImage(image)
            let outputExtension = ImageCompressorOptions.getOutputInString(options.output)
            
            var actualHeight = image.size.height
            var actualWidth = image.size.width
            let maxHeight: CGFloat = CGFloat(options.maxHeight)
            let maxWidth: CGFloat = CGFloat(options.maxWidth)
            var imgRatio = actualWidth / actualHeight
            let maxRatio = maxWidth / maxHeight
            let compressionQuality: CGFloat = CGFloat(options.quality)
            
            if actualHeight > maxHeight || actualWidth > maxWidth {
                if imgRatio < maxRatio {
                    imgRatio = maxHeight / actualHeight
                    actualWidth = imgRatio * actualWidth
                    actualHeight = maxHeight
                } else if imgRatio > maxRatio {
                    imgRatio = maxWidth / actualWidth
                    actualHeight = imgRatio * actualHeight
                    actualWidth = maxWidth
                } else {
                    actualHeight = maxHeight
                    actualWidth = maxWidth
                }
            }
            
            let rect = CGRect(x: 0.0, y: 0.0, width: actualWidth, height: actualHeight)
            UIGraphicsBeginImageContext(rect.size)
            image.draw(in: rect)
            let isBase64 = options.returnableOutputType == .rbase64
            
            if let img = UIGraphicsGetImageFromCurrentImageContext() {
                return writeImage(img, output: options.output.rawValue, quality: Float(compressionQuality), outputExtension: outputExtension, isBase64: isBase64, disablePngTransparency: options.disablePngTransparency,isEnableAutoCompress: true,actualImagePath: imagePath)
            }
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
            var resizeMode = RCTResizeMode.contain
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
