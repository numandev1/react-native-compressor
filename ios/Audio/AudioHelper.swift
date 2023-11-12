//
//  AudioHelper.swift
//  react-native-compressor
//
//  Created by Numan on 12/11/2023.
//

import AVFoundation

class AudioHelper {
    
    static func getAudioBitrate(path: String) -> Int {
        let audioURL = URL(fileURLWithPath: path)
        let avAsset = AVURLAsset(url: audioURL)
        let keys: Set<URLResourceKey> = [.totalFileSizeKey, .fileSizeKey]
        let resourceValues = try? audioURL.resourceValues(forKeys: keys)
        let fileSize = resourceValues?.fileSize ?? resourceValues?.totalFileSize

        // Calculate bitrate in kbps
        if let fileSize = fileSize, avAsset.duration.seconds > 0 {
            return Int(Double(fileSize) * 8 / avAsset.duration.seconds)
        }
        return 0
    }
    
    static func getDestinationBitrateByQuality(path: String, quality: String) -> Int {
            let originalBitrate = getAudioBitrate(path: path)
            var destinationBitrate = originalBitrate
            print("source bitrate: \(originalBitrate)")

            // Calculate the percentage of the original bitrate relative to the range 64000 to 320000
            let percentage = Double(originalBitrate - 64000) / Double(320000 - 64000)

            switch quality.lowercased() {
            case "low":
                destinationBitrate = max(64000, Int(Double(originalBitrate) * 0.3))
            case "medium":
                // Set destination bitrate to 60% of the original bitrate
                destinationBitrate = Int(Double(originalBitrate) * 0.5)
            case "high":
                // Set destination bitrate to 80% of the original bitrate
                destinationBitrate = min(320000,Int(Double(originalBitrate) * 0.7))
            default:
                print("Invalid quality level. Please enter 'low', 'medium', or 'high'.")
            }
            
            return destinationBitrate
        }
}
