import Foundation

class Downloader: NSObject, URLSessionDownloadDelegate {
    private var session: URLSession?
    private var task: URLSessionDownloadTask?
    private var statusCode: NSNumber?
    private var lastProgressEmitTimestamp: TimeInterval = 0
    private var lastProgressValue: NSNumber?
    private var contentLength: NSNumber?
    private var bytesWritten: NSNumber?
    private var resumeData: Data?
    private var toFile: String?
    private var progressDivider: Int=0
    
    typealias ProgressCallback = (NSNumber) -> Void
    typealias DownloadCompleteCallback = (String) -> Void
    typealias ErrorCallback = (Error) -> Void
    
    var globalProgressCallback: ProgressCallback?
    var globalDownloadCompleteCallback: DownloadCompleteCallback?
    var globalErrorCallback: ErrorCallback?
    
    private var fileHandle: FileHandle?
    
    func generateCacheFilePath(extension ext: String) -> String {
        let uuid = UUID()
        let imageNameWithoutExtension = uuid.uuidString
        let imageName = imageNameWithoutExtension + "." + ext
        let filePath = URL(fileURLWithPath: NSTemporaryDirectory()).appendingPathComponent(imageName).path
        return filePath
    }
    
    static func downloadFileAndSaveToCache(_ fileUrl: String, uuid: String,progressDivider: Int, completion: @escaping (String) -> Void) {
        let downloader = Downloader()
        
        downloader.downloadFile(fromUrl: fileUrl,progressDivider: progressDivider) { filePath in
            MediaCache.addCompletedImagePath(filePath)
            print("download completed file path: \(filePath)")
            completion(filePath)
        } progressCallback: { progress in
            EventEmitterHandler.emitDownloadProgress(progress, uuid: uuid)
        } errorCallback: { error in
            print("error downloadFile", error)
        }
    }
    
    func downloadFile(fromUrl: String, progressDivider: Int, downloadCompleteCallback: @escaping DownloadCompleteCallback, progressCallback: @escaping ProgressCallback, errorCallback: @escaping ErrorCallback) -> String? {
        globalProgressCallback = progressCallback
        globalDownloadCompleteCallback = downloadCompleteCallback
        globalErrorCallback = errorCallback
        self.progressDivider=progressDivider
        
        lastProgressEmitTimestamp = 0
        bytesWritten = 0
        
        guard let url = URL(string: fromUrl) else {
            let error = NSError(domain: "Downloader", code: NSURLErrorBadURL, userInfo: [NSLocalizedDescriptionKey: "Invalid URL"])
            globalErrorCallback?(error)
            return nil
        }
        
        let fileExtension = url.pathExtension
        toFile = generateCacheFilePath(extension: fileExtension)
        
        if FileManager.default.fileExists(atPath: toFile!) {
            fileHandle = FileHandle(forWritingAtPath: toFile!)
            
            if fileHandle == nil {
                let error = NSError(domain: "Downloader", code: NSURLErrorFileDoesNotExist, userInfo: [NSLocalizedDescriptionKey: "Failed to write target file at path: \(toFile!)"])
                globalErrorCallback?(error)
                return nil
            } else {
                fileHandle?.closeFile()
            }
        }
        
        let config = URLSessionConfiguration.default
        session = URLSession(configuration: config, delegate: self, delegateQueue: nil)
        task = session?.downloadTask(with: url)
        task?.resume()
        
        return nil
    }
    
    func urlSession(_ session: URLSession, downloadTask: URLSessionDownloadTask, didWriteData bytesWritten: Int64, totalBytesWritten: Int64, totalBytesExpectedToWrite: Int64) {
        guard let httpResponse = downloadTask.response as? HTTPURLResponse else { return }
        
        statusCode = NSNumber(value: httpResponse.statusCode)
        contentLength = NSNumber(value: httpResponse.expectedContentLength)
        
        if statusCode?.intValue == 200 {
            self.bytesWritten = NSNumber(value: totalBytesWritten)
            
            let doubleBytesWritten = Double(truncating: self.bytesWritten!)
            let doubleContentLength = Double(truncating: contentLength!)
            let doublePercents = doubleBytesWritten / doubleContentLength * 100
            let progress = NSNumber(value: floor(doublePercents))
            
            if self.progressDivider==0||progress.intValue % self.progressDivider == 0 {
                if (progress != lastProgressValue) || (bytesWritten == contentLength as! Int64) {
                    lastProgressValue = progress
                    let progressPercentage = NSNumber(value: Double(truncating: self.bytesWritten!) / Double(truncating: contentLength!))
                    globalProgressCallback?(progressPercentage)
                }
            }
        }
    }
    
    func urlSession(_ session: URLSession, downloadTask: URLSessionDownloadTask, didFinishDownloadingTo location: URL) {
        guard let httpResponse = downloadTask.response as? HTTPURLResponse else { return }
        
        if statusCode == nil {
            statusCode = NSNumber(value: httpResponse.statusCode)
        }
        
        let destURL = URL(fileURLWithPath: toFile!)
        let fm = FileManager.default
        
        do {
            if statusCode!.intValue >= 200 && statusCode!.intValue < 300 {
                do {
                    try fm.removeItem(at: destURL) // Remove file at destination path, if it exists
                }
                catch {
                    print("unable to remove item \(error)")
                }
                try fm.moveItem(at: location, to: destURL)
                bytesWritten = NSNumber(value: try fm.attributesOfItem(atPath: toFile!)[.size] as! Int64)
            }
        } catch {
            print("Downloader: Unable to move tempfile to destination. \(error)")
        }
        
        // Manually flush and invalidate the session to free up space
        if session != nil {
            session.flush(completionHandler: {
                session.finishTasksAndInvalidate()
            })
        }
        
        let fileURL = URL(fileURLWithPath: toFile!)
        globalDownloadCompleteCallback?(fileURL.absoluteString)
    }
    
    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        if let error = error {
            print("Downloader: didCompleteWithError \(error), \(error.localizedDescription)")
            if error._code != NSURLErrorCancelled {
                resumeData = (error as NSError).userInfo[NSURLSessionDownloadTaskResumeData] as? Data
                
                if resumeData == nil {
                    globalErrorCallback?(error)
                }
            }
        }
    }
}
