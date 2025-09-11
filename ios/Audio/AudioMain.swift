//
//  AudioMain.swift
//  react-native-compressor
//
//  Created by Numan on 10/09/2023.
//


import AVFoundation
class AudioMain{
    static func compress_audio(_ fileUrl: String, optionMap: NSDictionary, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        do {
            let fileUrl = fileUrl.replacingOccurrences(of: "file://", with: "")
            let fileManager = FileManager.default
            var isDir: ObjCBool = false
            if !fileManager.fileExists(atPath: fileUrl, isDirectory: &isDir) || isDir.boolValue {
                let err = NSError(domain: "file not found", code: -15, userInfo: nil)
                reject(String(err.code), err.localizedDescription, err)
                return
            }

            let audioOptions = AudioOptions.fromDictionary((optionMap as! [String : Any]))
            let outputMp3Path = Utils.generateCacheFilePath("m4a")

            let oldUfileUrlRL = URL(fileURLWithPath: fileUrl)
            let newURL = URL(fileURLWithPath: outputMp3Path)

            var options = FormatConverter.Options()

            if(audioOptions.samplerate != -1){
                options.sampleRate = Double(audioOptions.samplerate)
            }

            if(audioOptions.bitrate != -1){
                options.bitRate = UInt32(audioOptions.bitrate)
            }
            else
            {
                let bitrate = AudioHelper.getDestinationBitrateByQuality(path: fileUrl,quality: audioOptions.quality)
                print("output bitrate: \(bitrate)")
                options.bitRate = UInt32(bitrate)
            }


            if(audioOptions.channels != -1){
                options.channels = UInt32(audioOptions.channels)
            }

            options.format = .m4a
            options.eraseFile = false
            options.bitDepthRule = .any

            let converter = FormatConverter(inputURL: oldUfileUrlRL, outputURL: newURL, options: options)
            converter.start { error in
            // check to see if error isn't nil, otherwise you're good
                if((error) != nil)
                {
                    print("error=> \(error?.localizedDescription)")
                    reject(error?.localizedDescription,error?.localizedDescription, error)
                    return
                }

                resolve(newURL.absoluteString)
            }

        } catch {
            reject(error.localizedDescription, error.localizedDescription, nil)
        }
    }
}
