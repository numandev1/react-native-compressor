#import "Compressor.h"
#import <React/RCTBridgeModule.h>
//Image
#import "Image/ImageCompressor.h"
#import "Image/ImageCompressorOptions.h"
#import <React/RCTEventEmitter.h>
#import <AVFoundation/AVFoundation.h>


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

//Audio
RCT_EXPORT_METHOD(
    compress_audio: (NSString*) filePath
    resolver: (RCTPromiseResolveBlock) resolve
    rejecter: (RCTPromiseRejectBlock) reject) {
    @try {
        if([filePath containsString:@"file://"])
        {
            filePath=[filePath stringByReplacingOccurrencesOfString:@"file://"
                                                    withString:@""];
        }
        NSFileManager *fileManager = [NSFileManager defaultManager];
        BOOL isDir;
        if (![fileManager fileExistsAtPath:filePath isDirectory:&isDir] || isDir){
            NSError *err = [NSError errorWithDomain:@"file not found" code:-15 userInfo:nil];
            reject([NSString stringWithFormat: @"%lu", (long)err.code], err.localizedDescription, err);
            return;
        }

          NSDictionary *assetOptions = @{AVURLAssetPreferPreciseDurationAndTimingKey: @YES};
          AVURLAsset *asset = [AVURLAsset URLAssetWithURL:[NSURL fileURLWithPath:filePath] options:assetOptions];
       
       
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

//general
    RCT_EXPORT_METHOD(
        getFileSize: (NSString*) filePath
        resolver: (RCTPromiseResolveBlock) resolve
        rejecter: (RCTPromiseRejectBlock) reject) {
        @try {
            if([filePath containsString:@"file://"])
            {
                filePath=[filePath stringByReplacingOccurrencesOfString:@"file://"
                                                        withString:@""];
            }
            NSFileManager *fileManager = [NSFileManager defaultManager];
            BOOL isDir;
            if (![fileManager fileExistsAtPath:filePath isDirectory:&isDir] || isDir){
                NSError *err = [NSError errorWithDomain:@"file not found" code:-15 userInfo:nil];
                reject([NSString stringWithFormat: @"%lu", (long)err.code], err.localizedDescription, err);
                return;
            }
            NSDictionary *attrs = [fileManager attributesOfItemAtPath: filePath error: NULL];
            UInt32 fileSize = [attrs fileSize];
            NSString *fileSizeString = [@(fileSize) stringValue];
            resolve(fileSizeString);
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

