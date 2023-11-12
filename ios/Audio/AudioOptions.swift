class AudioOptions: NSObject {
    static func fromDictionary(_ dictionary: [String: Any]?) -> AudioOptions {
        let options = AudioOptions()
        
        guard let dictionary = dictionary else {
            return options
        }
        
        for (key, value) in dictionary {
            switch key {
            case "quality":
                options.quality = (value as? String) ?? "medium"
            case "bitrate":
                options.bitrate = (value as? Int) ?? -1
            case "samplerate":
                options.samplerate = (value as? Int) ?? -1
            case "channels":
                options.channels = (value as? Int) ?? -1
           
            default:
                break
            }
        }
        
        return options
    }

    var quality: String = "medium"
    var bitrate: Int = -1
    var samplerate: Int = -1
    var channels: Int = -1
   
    
    override init() {
        super.init()
    }
}
