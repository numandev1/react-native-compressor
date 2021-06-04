#import "Compressor.h"
#import <React/RCTBridgeModule.h>
//Image
#import "Image/Utils/ImageCompressor.h"
#import "Image/Utils/ImageCompressorOptions.h"
#import <React/RCTEventEmitter.h>

@implementation Compressor

RCT_EXPORT_MODULE()

//Image
RCT_EXPORT_METHOD(
    image_compress: (NSString*) value
    optionsDict: (NSDictionary*) optionsDict
    resolver: (RCTPromiseResolveBlock) resolve
    rejecter: (RCTPromiseRejectBlock) reject) {
    @try {
        ImageCompressorOptions *options = [ImageCompressorOptions fromDictionary:optionsDict];

        UIImage *image;
        switch (options.input) {
            case base64:
                image = [ImageCompressor decodeImage: value];
                break;
            case uri:
                image = [ImageCompressor loadImage: value];
                break;
            default:
                reject(@"unsupported_value", @"Unsupported value type.", nil);
                return;
        }
        NSString *outputExtension=[ImageCompressorOptions getOutputInString:options.output];
        UIImage *resizedImage = [ImageCompressor resize:image maxWidth:options.maxWidth maxHeight:options.maxHeight];
        Boolean isBase64=options.returnableOutputType ==rbase64;
        NSString *result = [ImageCompressor compress:resizedImage output:options.output quality:options.quality outputExtension:outputExtension isBase64:isBase64];
        resolve(result);
    }
    @catch (NSException *exception) {
        reject(exception.name, exception.reason, nil);
    }
}

//general
RCT_EXPORT_METHOD(
    generateFile: (NSString*) extension
    resolver: (RCTPromiseResolveBlock) resolve
    rejecter: (RCTPromiseRejectBlock) reject) {
    @try {
        NSString *outputUri =[ImageCompressor generateCacheFilePath:extension];
        resolve(outputUri);
    }
    @catch (NSException *exception) {
        reject(exception.name, exception.reason, nil);
    }
}

@end


@interface RCT_EXTERN_MODULE(VideoCompressor, RCTEventEmitter)

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

