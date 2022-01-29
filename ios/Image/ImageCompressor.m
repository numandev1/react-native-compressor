#import <Accelerate/Accelerate.h>
#import <CoreGraphics/CoreGraphics.h>
#import <Photos/Photos.h>
#import <React/RCTUtils.h>
#import <React/RCTImageLoader.h>
#import "ImageCompressor.h"
#import <React/RCTConvert.h>
#import <Foundation/Foundation.h>
#import <MobileCoreServices/MobileCoreServices.h>

@implementation ImageCompressor
+ (CGSize) findTargetSize: (UIImage *) image maxWidth: (int) maxWidth maxHeight: (int) maxHeight {
    CGFloat width = image.size.width;
    CGFloat height = image.size.height;
    
    if (width > height) {
        CGFloat newHeight = height / (width / maxWidth);
        return CGSizeMake(maxWidth, newHeight);
    }
    
    CGFloat newWidth = width / (height / maxHeight);
    return CGSizeMake(newWidth, maxHeight);
}

+(UIImage *) decodeImage: (NSString *) value {
    NSData *data = [[NSData alloc] initWithBase64EncodedString:value options: NSDataBase64DecodingIgnoreUnknownCharacters];
    return [[UIImage alloc] initWithData:data];
}

+(UIImage *) loadImage:(NSString *)path {
    UIImage *image=nil;
    if ([path hasPrefix:@"data:"] || [path hasPrefix:@"file:"]) {
            NSURL *imageUrl = [[NSURL alloc] initWithString:path];
            image = [UIImage imageWithData:[NSData dataWithContentsOfURL:imageUrl]];
          } else {
            image = [[UIImage alloc] initWithContentsOfFile:path];
          }
    return image;
}


+(UIImage *)manualResize:(UIImage *)image maxWidth:(int)maxWidth maxHeight:(int)maxHeight {
    CGSize targetSize = [self findTargetSize:image maxWidth:maxWidth maxHeight:maxHeight];
    
    CGImageRef cgImage = image.CGImage;
    
    int sourceWidth = image.size.width;
    int sourceHeight = image.size.height;
    int targetWidth = targetSize.width;
    int targetHeight = targetSize.height;
    int bytesPerPixel = 4;
    int sourceBytesPerRow = sourceWidth * bytesPerPixel;
    int targetBytesPerRow = targetWidth * bytesPerPixel;
    int bitsPerComponent = 8;
    
    CGColorSpaceRef colorSpace = CGColorSpaceCreateDeviceRGB();
    
    unsigned char *sourceData = (unsigned char*)calloc(sourceHeight * sourceWidth * bytesPerPixel, sizeof(unsigned char));
    
    CGContextRef context = CGBitmapContextCreate(sourceData, sourceWidth, sourceHeight,
                                                 bitsPerComponent, sourceBytesPerRow, colorSpace,
                                                 kCGImageAlphaPremultipliedFirst | kCGBitmapByteOrder32Big);
    CGContextDrawImage(context, CGRectMake(0, 0, sourceWidth, sourceHeight), cgImage);
    CGContextRelease(context);
    
    unsigned char *targetData = (unsigned char*)calloc(targetWidth * targetHeight * bytesPerPixel, sizeof(unsigned char));
    
    vImage_Buffer srcBuffer = {
        .data = sourceData,
        .width = sourceWidth,
        .height = sourceHeight,
        .rowBytes = sourceBytesPerRow
    };
    vImage_Buffer targetBuffer = {
        .data = targetData,
        .width = targetWidth,
        .height = targetHeight,
        .rowBytes = targetBytesPerRow
    };
    
    vImage_Error error = vImageScale_ARGB8888(&srcBuffer, &targetBuffer, nil, kvImageHighQualityResampling);
    free(sourceData);
    if (error != kvImageNoError) {
        free(targetData);
        NSException *exception = [[NSException alloc] initWithName: @"drawing_erro" reason:@"Problem while rendering your image" userInfo:nil];
        @throw exception;
    }
    
    CGContextRef targetContext = CGBitmapContextCreate(targetData, targetWidth, targetHeight,
                                                     bitsPerComponent, targetBytesPerRow, colorSpace,
                                                     kCGImageAlphaPremultipliedFirst | kCGBitmapByteOrder32Big);
    CGImageRef targetRef = CGBitmapContextCreateImage(targetContext);
    
    UIImage* resizedImage = [UIImage imageWithCGImage:targetRef];
    
    CGImageRelease(targetRef);
    CGColorSpaceRelease(colorSpace);
    CGContextRelease(targetContext);
    
    
    free(targetData);
    
    return resizedImage;
}

+(NSString *)manualCompress:(UIImage *)image output:(enum OutputType)output quality:(float)quality outputExtension:(NSString*)outputExtension isBase64:(Boolean)isBase64{
    NSData *data;
    NSException *exception;
    
    switch (output) {
        case jpg:
            data = UIImageJPEGRepresentation(image, quality);
            break;
        case png:
            data = UIImagePNGRepresentation(image);
            break;
        default:
            exception = [[NSException alloc] initWithName: @"unsupported_format" reason:@"This format is not supported." userInfo:nil];
            @throw exception;
    }
  
    if(isBase64)
    {
        return [data base64EncodedStringWithOptions:0];
    }
    else
    {
        NSString *filePath =[self generateCacheFilePath:outputExtension];
        [data writeToFile:filePath atomically:YES];
        NSString *returnablePath=[self makeValidUri:filePath];
        return returnablePath;
    }
}

+ (NSString *)generateCacheFilePath:(NSString *)extension{
    NSUUID *uuid = [NSUUID UUID];
    NSString *imageNameWihtoutExtension = [uuid UUIDString];
    NSString *imageName=[imageNameWihtoutExtension stringByAppendingPathExtension:extension];
    NSString *filePath =
        [NSTemporaryDirectory() stringByAppendingPathComponent:imageName];
    return filePath;
}

+(UIImage *)scaleAndRotateImage:(UIImage *)image{
        // No-op if the orientation is already correct
        if (image.imageOrientation == UIImageOrientationUp) return image;

        // We need to calculate the proper transformation to make the image upright.
        // We do it in 2 steps: Rotate if Left/Right/Down, and then flip if Mirrored.
        CGAffineTransform transform = CGAffineTransformIdentity;

        switch (image.imageOrientation) {
            case UIImageOrientationDown:
            case UIImageOrientationDownMirrored:
                transform = CGAffineTransformTranslate(transform, image.size.width, image.size.height);
                transform = CGAffineTransformRotate(transform, M_PI);
                break;

            case UIImageOrientationLeft:
            case UIImageOrientationLeftMirrored:
                transform = CGAffineTransformTranslate(transform, image.size.width, 0);
                transform = CGAffineTransformRotate(transform, M_PI_2);
                break;

            case UIImageOrientationRight:
            case UIImageOrientationRightMirrored:
                transform = CGAffineTransformTranslate(transform, 0, image.size.height);
                transform = CGAffineTransformRotate(transform, -M_PI_2);
                break;
            case UIImageOrientationUp:
            case UIImageOrientationUpMirrored:
                break;
        }

        switch (image.imageOrientation) {
            case UIImageOrientationUpMirrored:
            case UIImageOrientationDownMirrored:
                transform = CGAffineTransformTranslate(transform, image.size.width, 0);
                transform = CGAffineTransformScale(transform, -1, 1);
                break;

            case UIImageOrientationLeftMirrored:
            case UIImageOrientationRightMirrored:
                transform = CGAffineTransformTranslate(transform, image.size.height, 0);
                transform = CGAffineTransformScale(transform, -1, 1);
                break;
            case UIImageOrientationUp:
            case UIImageOrientationDown:
            case UIImageOrientationLeft:
            case UIImageOrientationRight:
                break;
        }

        // Now we draw the underlying CGImage into a new context, applying the transform
        // calculated above.
        CGContextRef ctx = CGBitmapContextCreate(NULL, image.size.width, image.size.height,
                                                 CGImageGetBitsPerComponent(image.CGImage), 0,
                                                 CGImageGetColorSpace(image.CGImage),
                                                 CGImageGetBitmapInfo(image.CGImage));
        CGContextConcatCTM(ctx, transform);
        switch (image.imageOrientation) {
            case UIImageOrientationLeft:
            case UIImageOrientationLeftMirrored:
            case UIImageOrientationRight:
            case UIImageOrientationRightMirrored:
                // Grr...
                CGContextDrawImage(ctx, CGRectMake(0,0,image.size.height,image.size.width), image.CGImage);
                break;

            default:
                CGContextDrawImage(ctx, CGRectMake(0,0,image.size.width,image.size.height), image.CGImage);
                break;
        }

        // And now we just create a new UIImage from the drawing context
        CGImageRef cgimg = CGBitmapContextCreateImage(ctx);
        UIImage *img = [UIImage imageWithCGImage:cgimg];
        CGContextRelease(ctx);
        CGImageRelease(cgimg);
        return img;
}

+(NSString *)makeValidUri:(NSString *)filePath{
    NSURL *fileWithUrl = [NSURL fileURLWithPath:filePath];
    NSURL *absoluteUrl = [fileWithUrl URLByDeletingLastPathComponent];
    NSString *fileUrl = [NSString stringWithFormat:@"file://%@/%@", [absoluteUrl path] , [fileWithUrl lastPathComponent]];
    return fileUrl;
}

+(NSString *)manualCompressHandler:(NSString *)imagePath options:(ImageCompressorOptions*)options{
    NSException *exception;
    UIImage *image;
    switch (options.input) {
        case base64:
            image = [ImageCompressor decodeImage: imagePath];
            break;
        case uri:
            image = [ImageCompressor loadImage: imagePath];
            break;
        default:
            exception = [[NSException alloc] initWithName: @"unsupported_value" reason:@"Unsupported value type.." userInfo:nil];
            @throw exception;
    }
    image=[ImageCompressor scaleAndRotateImage:image];
    NSString *outputExtension=[ImageCompressorOptions getOutputInString:options.output];
    UIImage *resizedImage = [ImageCompressor manualResize:image maxWidth:options.maxWidth maxHeight:options.maxHeight];
    Boolean isBase64=options.returnableOutputType ==rbase64;
    NSString *result = [ImageCompressor manualCompress:resizedImage output:options.output quality:options.quality outputExtension:outputExtension isBase64:isBase64];
    return result;
}

+(NSString *)autoCompressHandler:(NSString *)imagePath options:(ImageCompressorOptions*)options{
    
    NSException *exception;
    UIImage *image=[ImageCompressor loadImage: imagePath];;
    image=[ImageCompressor scaleAndRotateImage:image];
    NSString *outputExtension=[ImageCompressorOptions getOutputInString:options.output];
    
    
        float actualHeight = image.size.height;
        float actualWidth = image.size.width;
        float maxHeight = 1280.0;
        float maxWidth = 1280.0;
        float imgRatio = actualWidth/actualHeight;
        float maxRatio = maxWidth/maxHeight;
        float compressionQuality = 0.8;
        
        if (actualHeight > maxHeight || actualWidth > maxWidth) {
            if(imgRatio < maxRatio){
                imgRatio = maxHeight / actualHeight;
                actualWidth = imgRatio * actualWidth;
                actualHeight = maxHeight;
            }
            else if(imgRatio > maxRatio){
                imgRatio = maxWidth / actualWidth;
                actualHeight = imgRatio * actualHeight;
                actualWidth = maxWidth;
            }else{
                actualHeight = maxHeight;
                actualWidth = maxWidth;
            }
        }
        
        CGRect rect = CGRectMake(0.0, 0.0, actualWidth, actualHeight);
        UIGraphicsBeginImageContext(rect.size);
        [image drawInRect:rect];
        UIImage *img = UIGraphicsGetImageFromCurrentImageContext();
        NSData *imageData = UIImageJPEGRepresentation(img, compressionQuality);
        UIGraphicsEndImageContext();
        NSString *filePath =[self generateCacheFilePath:outputExtension];
        [imageData writeToFile:filePath atomically:YES];
        NSString *returnablePath=[self makeValidUri:filePath];
        return returnablePath;
}

+(void)getAbsoluteImagePath:(NSString *)imagePath completionHandler:(void (^)(NSString *absoluteImagePath))completionHandler
{
  if (![imagePath containsString:@"ph://"]) {
    completionHandler(imagePath);
    return;
  }
  NSURL *imageURL = [NSURL URLWithString:[imagePath stringByReplacingOccurrencesOfString:@" " withString:@"%20"]];
  CGSize size = CGSizeZero;
  CGFloat scale = 1;
  RCTResizeMode resizeMode = RCTResizeModeContain;
  NSString *assetID = @"";
  PHFetchResult *results;
  if (!imageURL) {
    NSString *errorText = @"Cannot load a photo library asset with no URL";
    @throw([NSException exceptionWithName:errorText reason:errorText userInfo:nil]);
  } else if ([imageURL.scheme caseInsensitiveCompare:@"assets-library"] == NSOrderedSame) {
    assetID = [imageURL absoluteString];
    results = [PHAsset fetchAssetsWithALAssetURLs:@[imageURL] options:nil];
  } else {
    assetID = [imageURL.absoluteString substringFromIndex:@"ph://".length];
    results = [PHAsset fetchAssetsWithLocalIdentifiers:@[assetID] options:nil];
  }
  if (results.count == 0) {
    NSString *errorText = [NSString stringWithFormat:@"Failed to fetch PHAsset with local identifier %@ with no error message.", assetID];
   @throw([NSException exceptionWithName:errorText reason:errorText userInfo:nil]);
  }
  PHAsset *asset = [results firstObject]; // <-- WE GOT THE ASSET
  PHImageRequestOptions *imageOptions = [PHImageRequestOptions new];
  imageOptions.networkAccessAllowed = YES;
  imageOptions.deliveryMode = PHImageRequestOptionsDeliveryModeHighQualityFormat;

  BOOL useMaximumSize = CGSizeEqualToSize(size, CGSizeZero);
  CGSize targetSize;
  if (useMaximumSize) {
    targetSize = PHImageManagerMaximumSize;
    imageOptions.resizeMode = PHImageRequestOptionsResizeModeNone;
  } else {
    targetSize = CGSizeApplyAffineTransform(size, CGAffineTransformMakeScale(scale, scale));
    imageOptions.resizeMode = PHImageRequestOptionsResizeModeFast;
  }

  PHImageContentMode contentMode = PHImageContentModeAspectFill;
  if (resizeMode == RCTResizeModeContain) {
    contentMode = PHImageContentModeAspectFit;
  }
  [[PHImageManager defaultManager] requestImageForAsset:asset
                                             targetSize:targetSize
                                            contentMode:contentMode
                                                options:imageOptions
                                          resultHandler:^(UIImage *result, NSDictionary<NSString *, id> *info) {
    // WE GOT THE IMAGE
    if (result) {
      UIImage *image = result;
      NSString *imageName = [assetID stringByReplacingOccurrencesOfString:@"/" withString:@"_"];
      NSString *imagePath = [self saveImageIntoCache:image withName:imageName];
    completionHandler(imagePath);
    } else {
        @throw([NSException exceptionWithName:@"image not found" reason:@"image not found" userInfo:nil]);
    }
  }];
}

+(NSString*) saveImageIntoCache:(UIImage *)image withName:(NSString *)name {
  NSData *imageData = UIImageJPEGRepresentation(image, 1);
    NSString *path =[self generateCacheFilePath:@"jpg"];
  [[NSFileManager defaultManager] createFileAtPath:path contents:imageData attributes:NULL];
  return path;
}

/// for video
    
+(void)getAbsoluteVideoPath:(NSString *)videoPath completionHandler:(void (^)(NSString *absoluteImagePath))completionHandler
{

    if (![videoPath containsString:@"ph://"]) {
      completionHandler(videoPath);
      return;
    }
    NSString *assetId =[videoPath stringByReplacingOccurrencesOfString:@"ph://"
                                                      withString:@""];
    AVFileType outputFileType = AVFileTypeMPEG4;
    NSString *pressetType = AVAssetExportPresetPassthrough;
    
    // Throwing some errors to the user if he is not careful enough
    if ([assetId isEqualToString:@""]) {
        NSError *error = [NSError errorWithDomain:@"RNGalleryManager" code: -91 userInfo:nil];
        @throw([NSException exceptionWithName:error reason:error userInfo:nil]);
        return;
    }
    
    // Getting Video Asset
    NSArray* localIds = [NSArray arrayWithObjects: assetId, nil];
    PHAsset * _Nullable videoAsset = [PHAsset fetchAssetsWithLocalIdentifiers:localIds options:nil].firstObject;
    
    // Getting information from the asset
    NSString *mimeType = (NSString *)CFBridgingRelease(UTTypeCopyPreferredTagWithClass((__bridge CFStringRef _Nonnull)(outputFileType), kUTTagClassMIMEType));
    CFStringRef uti = UTTypeCreatePreferredIdentifierForTag(kUTTagClassMIMEType, (__bridge CFStringRef _Nonnull)(mimeType), NULL);
     NSString *extension = (NSString *)CFBridgingRelease(UTTypeCopyPreferredTagWithClass(uti, kUTTagClassFilenameExtension));
    
    // Creating output url and temp file name
    NSString *path =[self generateCacheFilePath:extension];
    NSURL *outputUrl = [NSURL URLWithString:[@"file://" stringByAppendingString:path]];
    
    // Setting video export session options
    PHVideoRequestOptions *videoRequestOptions = [[PHVideoRequestOptions alloc] init];
    videoRequestOptions.networkAccessAllowed = YES;
    videoRequestOptions.deliveryMode = PHVideoRequestOptionsDeliveryModeHighQualityFormat;
    
    // Creating new export session
    [[PHImageManager defaultManager] requestExportSessionForVideo:videoAsset options:videoRequestOptions exportPreset:pressetType resultHandler:^(AVAssetExportSession * _Nullable exportSession, NSDictionary * _Nullable info) {
        
        exportSession.shouldOptimizeForNetworkUse = YES;
        exportSession.outputFileType = outputFileType;
        exportSession.outputURL = outputUrl;
        // Converting the video and waiting to see whats going to happen
        [exportSession exportAsynchronouslyWithCompletionHandler:^{
            switch ([exportSession status])
            {
                case AVAssetExportSessionStatusFailed:
                {
                    NSError* error = exportSession.error;
                    NSString *codeWithDomain = [NSString stringWithFormat:@"E%@%zd", error.domain.uppercaseString, error.code];
                    @throw([NSException exceptionWithName:error reason:error userInfo:nil]);
                    break;
                }
                case AVAssetExportSessionStatusCancelled:
                {
                    NSError *error = [NSError errorWithDomain:@"RNGalleryManager" code: -91 userInfo:nil];
                    @throw([NSException exceptionWithName:error reason:error userInfo:nil]);
                    break;
                }
                case AVAssetExportSessionStatusCompleted:
                {
                    completionHandler(outputUrl.absoluteString);
                    break;
                }
                default:
                {
                    NSError *error = [NSError errorWithDomain:@"RNGalleryManager" code: -91 userInfo:nil];
                    @throw([NSException exceptionWithName:@"Unknown status" reason:@"Unknown status" userInfo:nil]);
                    break;
                }
            }
        }];
    }];
}

@end
