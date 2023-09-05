//
//  Uploader.swift
//  react-native-compressor
//
//  Created by Numan on 10/09/2023.
//

import Foundation

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

      guard let file = URL(string: fileUrl) else{
        let uploadError = UploadError(message: "invalid file url")
        reject("Failed", "Upload Failed", uploadError)
        return
      }

      let headers = options["headers"] as? [String: String] ?? [:]

      let url = URL(string: remoteUrl)!
      var request = URLRequest(url: url)
      request.httpMethod=method
      for(header, v) in headers{
        request.setValue(v, forHTTPHeaderField: header)
      }

      uploadResolvers[uuid] = resolve
      uploadRejectors[uuid] = reject
      // TODO: ADD Headers
      let config = URLSessionConfiguration.background(withIdentifier: uuid)
      let session = URLSession(configuration: config, delegate: self, delegateQueue: nil)
      let task = session.uploadTask(with: request, fromFile: file)
      task.resume()
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
}
