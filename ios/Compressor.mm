#import "Compressor.h"

@interface RCT_EXTERN_MODULE(Compressor, RCTEventEmitter)

RCT_EXTERN_METHOD(image_compress: (NSString*) imagePath
                  withOptions: (NSDictionary*) optionMap
                  withResolver: (RCTPromiseResolveBlock) resolve
                  withRejecter: (RCTPromiseRejectBlock) reject)

RCT_EXTERN_METHOD(getImageMetaData: (NSString*) filePath
                  withResolver: (RCTPromiseResolveBlock) resolve
                  withRejecter: (RCTPromiseRejectBlock) reject)

RCT_EXTERN_METHOD(compress_audio: (NSString*) filePath
                  withOptions: (NSDictionary*) optionsDict
                  withResolver: (RCTPromiseResolveBlock) resolve
                  withRejecter: (RCTPromiseRejectBlock) reject)

RCT_EXTERN_METHOD(generateFilePath: (NSString*) _extension
                  withResolver: (RCTPromiseResolveBlock) resolve
                  withRejecter: (RCTPromiseRejectBlock) reject)

RCT_EXTERN_METHOD(getRealPath: (NSString*) path
                  withType: (NSString*) type
                  withResolver: (RCTPromiseResolveBlock) resolve
                  withRejecter: (RCTPromiseRejectBlock) reject)

RCT_EXTERN_METHOD(getFileSize: (NSString*) filePath
                  withResolver: (RCTPromiseResolveBlock) resolve
                  withRejecter: (RCTPromiseRejectBlock) reject)

RCT_EXTERN_METHOD(getVideoMetaData: (NSString*) filePath
                  withResolver: (RCTPromiseResolveBlock) resolve
                  withRejecter: (RCTPromiseRejectBlock) reject)

RCT_EXTERN_METHOD(compress:(NSString *)fileUrl
                 withOptions:(NSDictionary *)options
                 withResolver:(RCTPromiseResolveBlock)resolve
                 withRejecter:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(upload:(NSString *)fileUrl
                 withOptions:(NSDictionary *)options
                 withResolver:(RCTPromiseResolveBlock)resolve
                 withRejecter:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(cancelUpload:(NSString *)uuid
                  withShouldCancelAll:(BOOL*)shouldCancelAll)

RCT_EXTERN_METHOD(download:(NSString *)fileUrlu
                 withOptions:(NSDictionary *)options
                 withResolver:(RCTPromiseResolveBlock)resolve
                 withRejecter:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(activateBackgroundTask: (NSDictionary *)options
                 withResolver:(RCTPromiseResolveBlock)resolve
                 withRejecter:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(deactivateBackgroundTask: (NSDictionary *)options
                 withResolver:(RCTPromiseResolveBlock)resolve
                 withRejecter:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(createVideoThumbnail:(NSString *)fileUrl
                 withOptions:(NSDictionary *)options
                 withResolver:(RCTPromiseResolveBlock)resolve
                 withRejecter:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(clearCache:(NSString *)cacheDir
                 withResolver:(RCTPromiseResolveBlock)resolve
                 withRejecter:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(cancelCompression:(NSString *)uuid)

// Don't compile this code when we build for the old architecture.
#ifdef RCT_NEW_ARCH_ENABLED
- (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:
    (const facebook::react::ObjCTurboModule::InitParams &)params
{
    return std::make_shared<facebook::react::NativeCompressorSpecJSI>(params);
}
#endif

@end
