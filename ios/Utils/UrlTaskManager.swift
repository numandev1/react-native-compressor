//
//  UrlTaskManager.swift
//  react-native-compressor
//
//  Created by Numan on 14/11/2023.
//

import Foundation

class UrlTaskManager {
    private var resumableDownloads:[String: URLSessionTask] = [:]

    func registerTask(_ task: URLSessionTask, uuid: String) {
        resumableDownloads[uuid] = task
    }

    func taskForId(_ uuid: String) -> URLSessionTask? {
        return resumableDownloads[uuid]
    }
    
    

    // will use in future
    func downloadTaskForId(_ uuid: String) -> URLSessionDownloadTask? {
        let task = taskForId(uuid)
        return task as? URLSessionDownloadTask
    }

    func uploadTaskForId(_ uuid: String) -> URLSessionUploadTask? {
        let task = taskForId(uuid)
        return task as? URLSessionUploadTask
    }
    
    func taskPop() -> URLSessionTask? {
        if !resumableDownloads.isEmpty {
            let uuids = Array(resumableDownloads.keys)
            let lastUuid = uuids.last

            if let lastTask = resumableDownloads[lastUuid!] {
                resumableDownloads.removeValue(forKey: lastUuid!)
                return lastTask
            }
        }

        return nil
    }

    func unregisterTask(_ uuid: String) {
        resumableDownloads.removeValue(forKey: uuid)
    }
    
    func cancelAllTasks() {
            for (_, task) in resumableDownloads {
                task.cancel()
            }
            resumableDownloads.removeAll()
        }
}
