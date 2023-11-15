//
//  Uploader.swift
//  react-native-compressor
//
//  Created by Numan on 10/09/2023.
//

import Foundation
import MobileCoreServices

enum UploaderUploadType: Int {
    case UploaderInvalidType = -1
    case UploaderBinaryContent = 0
    case UploaderMultipart = 1
}

struct UploadError: Error {
  private let message: String

  var localizedDescription: String {
    return message
  }
  
  init(message: String) {
    self.message = message
  }
}

class Uploader : NSObject, URLSessionTaskDelegate{
    var uploadResolvers: [String: RCTPromiseResolveBlock] = [:]
    var uploadRejectors: [String: RCTPromiseRejectBlock] = [:]
    var currentTask: URLSessionDataTask?
    private lazy var taskManager = UrlTaskManager()
    
    func upload(filePath: String, options: [String: Any], resolve:@escaping RCTPromiseResolveBlock, reject:@escaping RCTPromiseRejectBlock) -> Void {
        let fileUrl = Utils.makeValidUri(filePath: filePath)
      
      guard let uuid = options["uuid"] as? String else {
        let uploadError = UploadError(message: "UUID is missing")
        reject("Upload Failed", "UUID is missing", uploadError)
        return
      }

      guard let remoteUrl = options["url"] as? String else {
        let uploadError = UploadError(message: "url is missing")
        reject("Upload Failed", "url is missing", uploadError)
        return
      }

      guard let method = options["method"] as? String else {
        let uploadError = UploadError(message: "method is missing")
        reject("Upload Failed", "method is missing", uploadError)
        return
      }

      guard let localFile = URL(string: fileUrl) else{
        let uploadError = UploadError(message: "invalid file url")
        reject("Failed", "Upload Failed", uploadError)
        return
      }
        
      let fieldName = options["fieldName"] as? String ?? "file"
      let mimeType = options["mimeType"] as? String ?? ""
      
      let parameters = options["parameters"] as? [String: String]
        
      let uploadType = options["uploadType"] as? Int ?? 0
          

      let headers = options["headers"] as? [String: String] ?? [:]

      let url = URL(string: remoteUrl)!
      var request = URLRequest(url: url)
      request.httpMethod=method
      for(header, v) in headers{
        request.setValue(v, forHTTPHeaderField: header)
      }

      uploadResolvers[uuid] = resolve
      uploadRejectors[uuid] = reject
        
      let type:UploaderUploadType=self.getUploadType(from: uploadType)
 
      let config = URLSessionConfiguration.background(withIdentifier: uuid)
      let session = URLSession(configuration: config, delegate: self, delegateQueue: nil)
        var task:URLSessionUploadTask!;
        if type == .UploaderBinaryContent {
            task = session.uploadTask(with: request, fromFile: localFile)
        } else if type == .UploaderMultipart {
            let boundaryString = UUID().uuidString
            let data = try? createMultipartBody(boundary: boundaryString, sourceUrl:localFile,parameters:parameters,fieldName:fieldName,mimeType:mimeType)

                request.setValue("multipart/form-data; boundary=\(boundaryString)", forHTTPHeaderField: "Content-Type")
                request.httpBody = data

              task=session.uploadTask(withStreamedRequest: request)
        }
        else {
            let errorMessage = String(format: "Invalid upload type: '%@'.", options["uploadType"] as? String ?? "")
            reject("ERR_FILESYSTEM_INVALID_UPLOAD_TYPE", errorMessage, nil)
        }
        taskManager.registerTask(task, uuid: uuid)
        task.resume()
     
    }
    
    func cancelUpload(uuid:String,shouldCancelAll:Bool) {
        if(shouldCancelAll==true)
        {
            taskManager.cancelAllTasks()
        } else if(uuid=="")
        {
         taskManager.taskPop()?.cancel()
        }
        else
        {
            taskManager.uploadTaskForId(uuid)?.cancel()
        }
        
    }

    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
      guard let uuid = session.configuration.identifier else {return}
      guard let reject = uploadRejectors[uuid] else{return}
      guard let resolve = uploadResolvers[uuid] else{return}
      guard error == nil else {
        reject("failed", "Upload Failed", error)
        uploadRejectors[uuid] = nil
        return;
      }

      guard let response = task.response  as? HTTPURLResponse else {
        let uploadError = UploadError(message: "Response is not defined")
        reject("failed", "Upload Failed", uploadError)
        uploadRejectors[uuid] = nil
        return;
      }

      let result: [String : Any] = ["status": response.statusCode, "headers": response.allHeaderFields, "body": ""]
      
      resolve(result)
      uploadResolvers[uuid] = nil
    }
      
    func urlSession(_ session: URLSession, task: URLSessionTask, didSendBodyData bytesSent: Int64, totalBytesSent: Int64, totalBytesExpectedToSend: Int64)
    {
      guard let uuid = session.configuration.identifier else {return}
        EventEmitterHandler.emituploadProgress(uuid,totalBytesSent: totalBytesSent,totalBytesExpectedToSend: totalBytesExpectedToSend)
    }
    
    func headersForMultipartParams(_ params: [String: String]?, boundary: String) -> String {
      guard let params else {
        return ""
      }
      return params.map { (key: String, value: String) in
    """
    --\(boundary)
    Content-Disposition: form-data; name="\(key)"

    \(value)
    """
      }
      .joined()
    }
    
    func createMultipartBody(boundary: String, sourceUrl: URL, parameters: [String: String]? = nil, fieldName: String? = nil, mimeType: String? = nil) throws -> Data {
        var body = Data()

        // Add boundary
        let boundaryPrefix = "--\(boundary)\r\n"
        body.append(boundaryPrefix.data(using: .utf8)!)

        // Add content disposition for the file
        var contentDisposition = "Content-Disposition: form-data; name=\"file\"; filename=\"\(sourceUrl.lastPathComponent)\"\r\n"
        if let fieldName = fieldName {
            contentDisposition = "Content-Disposition: form-data; name=\"\(fieldName)\"; filename=\"\(sourceUrl.lastPathComponent)\"\r\n"
        }
        body.append(contentDisposition.data(using: .utf8)!)

        // Add optional MIME type
        if let mimeType = mimeType {
            let contentType = "Content-Type: \(mimeType)\r\n"
            body.append(contentType.data(using: .utf8)!)
        }

        // Add blank line
        body.append("\r\n".data(using: .utf8)!)

        // Add file data
        let fileData = try Data(contentsOf: sourceUrl)
        body.append(fileData)

        // Add parameters, if any
        if let parameters = parameters {
            for (key, value) in parameters {
                body.append("\r\n".data(using: .utf8)!)
                body.append(boundaryPrefix.data(using: .utf8)!)
                let parameterContentDisposition = "Content-Disposition: form-data; name=\"\(key)\"\r\n\r\n"
                body.append(parameterContentDisposition.data(using: .utf8)!)
                body.append(value.data(using: .utf8)!)
            }
        }

        // Add closing boundary
        let closingBoundary = "\r\n--\(boundary)--\r\n"
        body.append(closingBoundary.data(using: .utf8)!)

        return body
    }
    
    func findMimeType(forAttachment attachment: URL) -> String {
      if let identifier = UTTypeCreatePreferredIdentifierForTag(kUTTagClassFilenameExtension, attachment.pathExtension as CFString, nil)?.takeRetainedValue() {
        if let type = UTTypeCopyPreferredTagWithClass(identifier, kUTTagClassMIMEType)?.takeRetainedValue() {
          return type as String
        }
      }
      return "application/octet-stream"
    }
    
    func getUploadType(from type: Int?) -> UploaderUploadType {
        guard let typeValue = type else {
            return .UploaderInvalidType
        }
        
        switch typeValue {
        case UploaderUploadType.UploaderBinaryContent.rawValue,
            UploaderUploadType.UploaderMultipart.rawValue:
            return UploaderUploadType(rawValue: typeValue) ?? .UploaderInvalidType
        default:
            return .UploaderInvalidType
        }
    }

    
}
