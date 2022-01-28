#import <UIKit/UIKit.h>

#import "ImageCompressorOptions.h"

@interface ImageCompressor : NSObject
+ (UIImage *)decodeImage:(NSString *)base64;
+ (UIImage *)loadImage:(NSString *)path;
+ (NSString *)generateCacheFilePath:(NSString *)extension;

+ (UIImage *)manualResize:(UIImage *)image maxWidth:(int)maxWidth maxHeight:(int)maxHeight;
+(NSString *)manualCompressHandler:(NSString *)imagePath options:(ImageCompressorOptions*)options;
+(NSString *)autoCompressHandler:(NSString *)imagePath options:(ImageCompressorOptions*)options;
+ (NSString *)manualCompress:(UIImage *)image output:(enum OutputType)output quality:(float)quality outputExtension:(NSString*)outputExtension isBase64:(Boolean)isBase64;
+ (UIImage *)scaleAndRotateImage:(UIImage *)image;
+ (void)getAbsoluteImagePath:(NSString *)imagePath completionHandler:(void (^)(NSString *absoluteImagePath))completionHandler;
+(void)getAbsoluteVideoPath:(NSString *)videoPath completionHandler:(void (^)(NSString *absoluteImagePath))completionHandler;
@end
