#import "Compressor.h"
#import "Video/VideoUpload.h"

@implementation Compressor

RCT_EXPORT_MODULE()

RCT_EXTERN_METHOD(compress:(NSString *)fileUrl
                 withOptions:(NSDictionary *)options
                 withResolver:(RCTPromiseResolveBlock)resolve
                 withRejecter:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(upload:(NSString *)fileUrl
                 withOptions:(NSDictionary *)options
                 withResolver:(RCTPromiseResolveBlock)resolve
                 withRejecter:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(activateBackgroundTask: (NSDictionary *)options
                 withResolver:(RCTPromiseResolveBlock)resolve
                 withRejecter:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(deactivateBackgroundTask: (NSDictionary *)options
                 withResolver:(RCTPromiseResolveBlock)resolve
                 withRejecter:(RCTPromiseRejectBlock)reject)


@end
