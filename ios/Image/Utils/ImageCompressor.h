#import <UIKit/UIKit.h>

#import "ImageCompressorOptions.h"

@interface ImageCompressor : NSObject
+ (UIImage *)decodeImage:(NSString *)base64;
+ (UIImage *)loadImage:(NSString *)path;

+ (UIImage *)resize:(UIImage *)image maxWidth:(int)maxWidth maxHeight:(int)maxHeight;
+ (NSString *)compress:(UIImage *)image output:(enum OutputType)output quality:(float)quality outputExtension:(NSString*)outputExtension isBase64:(Boolean)isBase64;
@end
