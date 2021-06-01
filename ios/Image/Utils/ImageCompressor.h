//
//  ImageCompressor.h
//  TRNReactNativeImageImageCompressor
//
//  Created by Leonard Breitkopf on 05/09/2019.
//  Copyright © 2019 nomi9995. All rights reserved.
//

#import <UIKit/UIKit.h>

#import "ImageCompressorOptions.h"

@interface ImageCompressor: NSObject
+ (UIImage*)decodeImage: (NSString*) base64;
+ (UIImage*)loadImage: (NSString*) path;

+ (UIImage*) resize: (UIImage*) image maxWidth: (int) maxWidth maxHeight: (int) maxHeight;
+ (NSString*) compress: (UIImage*) image output: (enum OutputType) output quality: (float) quality;
@end
