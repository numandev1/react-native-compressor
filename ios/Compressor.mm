#import "Compressor.h"

//Image
#import "Image/ImageCompressor.h"
#import "Image/ImageCompressorOptions.h"
#import <React/RCTEventEmitter.h>
#import <AVFoundation/AVFoundation.h>

#define AlAsset_Library_Scheme @"assets-library"
@implementation Compressor

AVAssetWriter *assetWriter=nil;
static NSArray *metadatas;

- (NSArray *)metadatas
{
  if (!metadatas) {
    metadatas = @[
      @"albumName",
      @"artist",
      @"comment",
      @"copyrights",
      @"creationDate",
      @"date",
      @"encodedby",
      @"genre",
      @"language",
      @"location",
      @"lastModifiedDate",
      @"performer",
      @"publisher",
      @"title"
    ];
  }
  return metadatas;
}

RCT_EXPORT_MODULE()


//Image
RCT_EXPORT_METHOD(
    image_compress: (NSString*) imagePath
    optionsDict: (NSDictionary*) optionsDict
    resolver: (RCTPromiseResolveBlock) resolve
    rejecter: (RCTPromiseRejectBlock) reject) {
    [self image_compress:imagePath optionMap:optionsDict resolve:resolve reject:reject];
}

//Audio
RCT_EXPORT_METHOD(
    compress_audio: (NSString*) filePath
    optionsDict: (NSDictionary*) optionsDict
    resolver: (RCTPromiseResolveBlock) resolve
    rejecter: (RCTPromiseRejectBlock) reject) {
    [self compress_audio:filePath optionMap:optionsDict resolve:resolve reject:reject];
}

//general
RCT_EXPORT_METHOD(
    generateFilePath: (NSString*) extension
    resolver: (RCTPromiseResolveBlock) resolve
    rejecter: (RCTPromiseRejectBlock) reject) {
    [self generateFilePath:extension resolve:resolve reject:reject];
}

RCT_EXPORT_METHOD(
    getRealPath: (NSString*) path
    type: (NSString*) type
    resolver: (RCTPromiseResolveBlock) resolve
    rejecter: (RCTPromiseRejectBlock) reject) {
    [self getRealPath:path type:type resolve:resolve reject:reject];
}

//general
    RCT_EXPORT_METHOD(
        getFileSize: (NSString*) filePath
        resolver: (RCTPromiseResolveBlock) resolve
        rejecter: (RCTPromiseRejectBlock) reject) {
        [self getFileSize:filePath resolve:resolve reject:reject];
    }


RCT_EXPORT_METHOD(
    getVideoMetaData: (NSString*) filePath
    resolver: (RCTPromiseResolveBlock) resolve
    rejecter: (RCTPromiseRejectBlock) reject) {
    [self getVideoMetaData:filePath resolve:resolve reject:reject];
}

- (NSString *)getAudioQualityConstant:(NSString *)quality
{
    NSMutableArray *audioQualityArray = [[NSMutableArray alloc]initWithObjects:@"low", @"medium", @"high", nil];
    int index = [audioQualityArray indexOfObject:quality];
    switch (index) {
        case 0:
            return AVAssetExportPresetLowQuality;
            break;
        case 1:
            return AVAssetExportPresetMediumQuality;
            break;
        case 2:
            return AVAssetExportPresetHighestQuality;
            break;
    }
    return AVAssetExportPresetMediumQuality;
}

- (void)audo_compress_helper:(AVURLAsset *)avAsset qualityConstant:(NSString *)qualityConstant complete:(void (^)(NSString *mp3Path, BOOL finished))completeCallback {
    NSString *path;
    if ([avAsset.URL.scheme isEqualToString:AlAsset_Library_Scheme]) {
        path = avAsset.URL.query;
        if (path.length == 0) {
            completeCallback(nil, NO);
            return;
        }
        
    }else {
        path = avAsset.URL.path;
        if (!path || ![[NSFileManager defaultManager] fileExistsAtPath:path]) {
            completeCallback(nil, NO);
            return;
        }
    }
    
    NSString *mp3Path = [ImageCompressor generateCacheFilePath:@"m4a"];;

    if ([[NSFileManager defaultManager] fileExistsAtPath:mp3Path]) {
        if (completeCallback)
            completeCallback(mp3Path, YES);
        return;
    }

    NSURL *mp3Url;
    NSArray *compatiblePresets = [AVAssetExportSession exportPresetsCompatibleWithAsset:avAsset];
    
    if ([compatiblePresets containsObject:qualityConstant]) {
        AVAssetExportSession *exportSession = [[AVAssetExportSession alloc]
                                               initWithAsset:avAsset
                                               presetName:AVAssetExportPresetAppleM4A];

        mp3Url = [NSURL fileURLWithPath:mp3Path];
        exportSession.outputURL = mp3Url;
        exportSession.shouldOptimizeForNetworkUse = YES;
        exportSession.outputFileType = AVFileTypeAppleM4A;
        
 
        [exportSession exportAsynchronouslyWithCompletionHandler:^{
            BOOL finished = NO;
            switch ([exportSession status]) {
                case AVAssetExportSessionStatusFailed:
                    NSLog(@"AVAssetExportSessionStatusFailed, error:%@.", exportSession.error);
                    break;

                case AVAssetExportSessionStatusCancelled:
                    NSLog(@"AVAssetExportSessionStatusCancelled.");
                    break;

                case AVAssetExportSessionStatusCompleted:
                    NSLog(@"AVAssetExportSessionStatusCompleted.");
                    finished = YES;
                    break;

                case AVAssetExportSessionStatusUnknown:
                    NSLog(@"AVAssetExportSessionStatusUnknown");
                    break;

                case AVAssetExportSessionStatusWaiting:
                    NSLog(@"AVAssetExportSessionStatusWaiting");
                    break;

                case AVAssetExportSessionStatusExporting:
                    NSLog(@"AVAssetExportSessionStatusExporting");
                    break;

            }

            if (completeCallback)
                completeCallback(mp3Path, finished);
        }];
        
    }
}

- (void)compress_audio:(NSString *)fileUrl optionMap:(NSDictionary *)optionMap resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject {
    @try {
            if([fileUrl containsString:@"file://"])
            {
                fileUrl=[fileUrl stringByReplacingOccurrencesOfString:@"file://"
                                                        withString:@""];
            }
            NSFileManager *fileManager = [NSFileManager defaultManager];
            BOOL isDir;
            if (![fileManager fileExistsAtPath:fileUrl isDirectory:&isDir] || isDir){
                NSError *err = [NSError errorWithDomain:@"file not found" code:-15 userInfo:nil];
                reject([NSString stringWithFormat: @"%lu", (long)err.code], err.localizedDescription, err);
                return;
            }

              NSDictionary *assetOptions = @{AVURLAssetPreferPreciseDurationAndTimingKey: @YES};
              AVURLAsset *asset = [AVURLAsset URLAssetWithURL:[NSURL fileURLWithPath:fileUrl] options:assetOptions];
            NSString *quality=[optionMap objectForKey:@"quality"];
            NSString *qualityConstant=[self getAudioQualityConstant:quality];
            [self audo_compress_helper:asset qualityConstant:qualityConstant complete:^(NSString *mp3Path, BOOL finished) {
                if(finished)
                {
                    resolve([NSString stringWithFormat: @"file:/%@", mp3Path]);
                }
                else
                {
                    reject(@"Error", @"Something went wrong", nil);
                }
                
            }];
           
        }
        @catch (NSException *exception) {
            reject(exception.name, exception.reason, nil);
        }
}

- (void)generateFilePath:(NSString *)extension resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject {
    @try {
            NSString *outputUri =[ImageCompressor generateCacheFilePath:extension];
            resolve(outputUri);
        }
        @catch (NSException *exception) {
            reject(exception.name, exception.reason, nil);
        }
}

- (void)getRealPath:(NSString *)path type:(NSString *)type resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject {
    @try {
            if([type isEqualToString:@"video"])
            {
                [ImageCompressor getAbsoluteVideoPath:path completionHandler:^(NSString* absoluteImagePath){
                    resolve(absoluteImagePath);
                }];
            }
            else
            {
                [ImageCompressor getAbsoluteImagePath:path completionHandler:^(NSString* absoluteImagePath){
                    resolve(absoluteImagePath);
                }];
            }
        }
        @catch (NSException *exception) {
            reject(exception.name, exception.reason, nil);
        }
}

- (void)getVideoMetaData:(NSString *)filePath resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject {
    @try {
            [ImageCompressor getAbsoluteVideoPath:filePath completionHandler:^(NSString *absoluteImagePath) {
                if([absoluteImagePath containsString:@"file://"])
                {
                    absoluteImagePath=[absoluteImagePath stringByReplacingOccurrencesOfString:@"file://"
                                                            withString:@""];
                }
                NSFileManager *fileManager = [NSFileManager defaultManager];

                  BOOL isDir;
                  if (![fileManager fileExistsAtPath:absoluteImagePath isDirectory:&isDir] || isDir){
                    NSError *err = [NSError errorWithDomain:@"file not found" code:-15 userInfo:nil];
                    reject([NSString stringWithFormat: @"%lu", (long)err.code], err.localizedDescription, err);
                    return;
                  }
                NSDictionary *attrs = [fileManager attributesOfItemAtPath: absoluteImagePath error: NULL];
                UInt32 fileSize = [attrs fileSize];
                NSString *fileSizeString = [@(fileSize) stringValue];

                  NSMutableDictionary *result = [NSMutableDictionary new];
                  NSDictionary *assetOptions = @{AVURLAssetPreferPreciseDurationAndTimingKey: @YES};
                  AVURLAsset *asset = [AVURLAsset URLAssetWithURL:[NSURL fileURLWithPath:absoluteImagePath] options:assetOptions];\
                  AVAssetTrack *avAsset = [[asset tracksWithMediaType:AVMediaTypeVideo] objectAtIndex:0];
                  CGSize size = [avAsset naturalSize];
                  NSString *extension = [[absoluteImagePath lastPathComponent] pathExtension];
                  CMTime time = [asset duration];
                  int seconds = ceil(time.value/time.timescale);
                  [result setObject:[NSString stringWithFormat: @"%.2f", size.width] forKey:@"width"];
                  [result setObject:[NSString stringWithFormat: @"%.2f", size.height] forKey:@"height"];
                  [result setObject:extension forKey:@"extension"];
                  [result setObject:fileSizeString forKey:@"size"];
                  [result setObject:[@(seconds) stringValue] forKey:@"duration"];
                  NSArray *keys = [NSArray arrayWithObjects:@"commonMetadata", nil];
                  [asset loadValuesAsynchronouslyForKeys:keys completionHandler:^{
                    // string keys
                    for (NSString *key in [self metadatas]) {
                      NSArray *items = [AVMetadataItem metadataItemsFromArray:asset.commonMetadata
                                                                     withKey:key
                                                                    keySpace:AVMetadataKeySpaceCommon];
                      for (AVMetadataItem *item in items) {
                        [result setObject:item.value forKey:key];
                      }
                    }
                    resolve(result);
                  }];
            }];
        }
        @catch (NSException *exception) {
            reject(exception.name, exception.reason, nil);
        }
}

- (void)image_compress:(NSString *)imagePath optionMap:(NSDictionary *)optionMap resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject {
    @try {
            ImageCompressorOptions *options = [ImageCompressorOptions fromDictionary:optionMap];
            [ImageCompressor getAbsoluteImagePath:imagePath completionHandler:^(NSString* absoluteImagePath){
                if(options.autoCompress)
                {
                    NSString *result = [ImageCompressor autoCompressHandler:absoluteImagePath options:options];
                    resolve(result);
                }
                else
                {
                    NSString *result = [ImageCompressor manualCompressHandler:absoluteImagePath options:options];
                    resolve(result);
                }
            }];
            
        }
        @catch (NSException *exception) {
            reject(exception.name, exception.reason, nil);
        }
}

- (void)getFileSize:(NSString *)filePath resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject {
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

// Don't compile this code when we build for the old architecture.
#ifdef RCT_NEW_ARCH_ENABLED
- (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:
    (const facebook::react::ObjCTurboModule::InitParams &)params
{
    return std::make_shared<facebook::react::NativeCompressorSpecJSI>(params);
}
#endif


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

RCT_EXTERN_METHOD(cancelCompression:(NSString *)uuid)

// Don't compile this code when we build for the old architecture.
#ifdef RCT_NEW_ARCH_ENABLED
- (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:
    (const facebook::react::ObjCTurboModule::InitParams &)params
{
    return std::make_shared<facebook::react::NativeVideoCompressorSpecJSI>(params);
}
#endif

@end
