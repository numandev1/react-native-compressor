#import <UIKit/UIKit.h>
#import <React/RCTEventEmitter.h>
#import "Compressor.h"
#import "ImageCompressorOptions.h"

@interface ImageCompressor : RCTEventEmitter<NSObject>
+ (UIImage *)decodeImage:(NSString *)base64;
+ (UIImage *)loadImage:(NSString *)path;
+ (NSString *)generateCacheFilePath:(NSString *)extension;

+ (UIImage *)manualResize:(UIImage *)image maxWidth:(int)maxWidth maxHeight:(int)maxHeight;
+ (NSString *)manualCompressHandler:(NSString *)imagePath options:(ImageCompressorOptions *)options;
+ (NSString *)autoCompressHandler:(NSString *)imagePath options:(ImageCompressorOptions *)options;
+ (NSString *)manualCompress:(UIImage *)image output:(enum OutputType)output quality:(float)quality outputExtension:(NSString *)outputExtension isBase64:(Boolean)isBase64;
+ (UIImage *)scaleAndRotateImage:(UIImage *)image;
+ (void)getAbsoluteImagePath:(NSString *)imagePath uuid:(NSString *)uuid completionHandler:(void (^)(NSString *absoluteImagePath))completionHandler;
+ (void)getAbsoluteVideoPath:(NSString *)videoPath uuid:(NSString *)uuid completionHandler:(void (^)(NSString *absoluteImagePath))completionHandler;
+ (void)downloadFileAndSaveToCache:(NSString *)fileUrl;
+ (void)getFileSizeFromURL:(NSString *)urlString completion:(void (^)(NSNumber *fileSize, NSError *error))completion;
+ (void)initCompressorInstance:(Compressor*)instance;
+ (void)initVideoCompressorInstance:(id)object;
@end
