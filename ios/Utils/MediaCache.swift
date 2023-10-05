import Foundation

class MediaCache {
    static var completedImagePaths: [String] = []

    class func addCompletedImagePath(_ imagePath: String) {
        if !imagePath.isEmpty {
            completedImagePaths.append(imagePath)
        }
    }

    class func removeCompletedImagePath(_ imagePath: String) {
        if !imagePath.isEmpty {
            if let index = completedImagePaths.firstIndex(of: imagePath) {
                completedImagePaths.remove(at: index)
                if let fileURL = URL(string: imagePath) {
                    let fileSystemPath = fileURL.path
                    
                    let fileManager = FileManager.default
                    do {
                        if fileManager.fileExists(atPath: fileSystemPath) {
                            try fileManager.removeItem(atPath: fileSystemPath)
                        }
                    } catch {
                        print("Error deleting image at path: \(fileSystemPath)")
                    }
                }
            }
        }
    }

    class func cleanupCache() {
        let fileManager = FileManager.default
        if let cacheDirectory = NSSearchPathForDirectoriesInDomains(.cachesDirectory, .userDomainMask, true).first {
            for imagePath in completedImagePaths {
                let absoluteImagePath = (cacheDirectory as NSString).appendingPathComponent(imagePath)
                do {
                    if fileManager.fileExists(atPath: absoluteImagePath) {
                        try fileManager.removeItem(atPath: absoluteImagePath)
                    }
                } catch {
                    print("Error deleting image at path: \(absoluteImagePath)")
                }
            }
            completedImagePaths.removeAll()
        }
    }
    
    class func deleteFile(atPath filePath: String) {
        let fileManager = FileManager.default
        var _filePath = filePath
        if _filePath.hasPrefix("file://") {
            _filePath = _filePath.replacingOccurrences(of: "file://", with: "")
       }
        
        if fileManager.fileExists(atPath: _filePath) {
            do {
                try fileManager.removeItem(atPath: _filePath)
                print("File deleted successfully: \(_filePath)")
            } catch {
                print("Error deleting file: \(error.localizedDescription)")
            }
        } else {
            print("File not found at path: \(_filePath)")
        }
    }

}
