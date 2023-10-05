import Foundation
import UIKit

enum OutputType: Int {
    case jpg
    case png
}

enum InputType: Int {
    case base64
    case uri
}

enum ReturnableOutputType: Int {
    case rbase64
    case ruri
}

class ImageCompressorOptions: NSObject {
    static func fromDictionary(_ dictionary: [String: Any]?) -> ImageCompressorOptions {
        let options = ImageCompressorOptions()
        
        guard let dictionary = dictionary else {
            return options
        }
        
        for (key, value) in dictionary {
            switch key {
            case "compressionMethod":
                options.autoCompress = (value as? String ?? "") != "manual"
            case "maxWidth":
                options.maxWidth = (value as? Int) ?? 1280
            case "maxHeight":
                options.maxHeight = (value as? Int) ?? 1280
            case "progressDivider":
                options.progressDivider = (value as? Int) ?? 0
            case "quality":
                options.quality = (value as? Float) ?? 0.8
            case "input":
                options.parseInput(value as? String)
            case "output":
                options.parseOutput(value as? String)
            case "returnableOutputType":
                options.parseReturnableOutput(value as? String)
            case "uuid":
                options.uuid = value as? String
            case "disablePngTransparency":
                options.disablePngTransparency = value as? Bool ?? false
            default:
                break
            }
        }
        
        return options
    }
    
    static func getOutputInString(_ output: OutputType) -> String {
        return (output == .jpg) ? "jpg" : "png"
    }
    
    var autoCompress: Bool = true
    var maxWidth: Int = 1280
    var maxHeight: Int = 1280
    var progressDivider: Int = 0
    var quality: Float = 0.8
    var input: InputType = .uri
    var output: OutputType = .jpg
    var returnableOutputType: ReturnableOutputType = .ruri
    var uuid: String?
    var disablePngTransparency: Bool = false
    
    override init() {
        super.init()
    }
    
    private func parseInput(_ input: String?) {
        guard let input = input else { return }
        
        let inputTranslations: [String: InputType] = ["base64": .base64, "uri": .uri]
        self.input = inputTranslations[input] ?? .uri
    }
    
    private func parseReturnableOutput(_ input: String?) {
        guard let input = input else { return }
        
        let outputTranslations: [String: ReturnableOutputType] = ["base64": .rbase64, "uri": .ruri]
        self.returnableOutputType = outputTranslations[input] ?? .ruri
    }
    
    private func parseOutput(_ output: String?) {
        guard let output = output else { return }
        
        let outputTranslations: [String: OutputType] = ["jpg": .jpg, "png": .png]
        self.output = outputTranslations[output] ?? .jpg
    }
}
