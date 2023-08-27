#import <Foundation/Foundation.h>

@interface MediaCache : NSObject

+ (instancetype)sharedCache;

+ (void)addCompletedImagePath:(NSString *)imagePath;
+ (void)removeCompletedImagePath:(NSString *)imagePath;
+ (void)cleanupCache;

@end
