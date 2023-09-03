#import "MediaCache.h"

@implementation MediaCache
NSMutableArray<NSString *> *completedImagePaths;

+ (void)addCompletedImagePath:(NSString *)imagePath {
    if (imagePath) {
        [completedImagePaths addObject:imagePath];
    }
}

+ (void)removeCompletedImagePath:(NSString *)imagePath {
    if (imagePath) {
        [completedImagePaths removeObject:imagePath];
        NSURL *fileURL = [NSURL URLWithString:imagePath];
        NSString *fileSystemPath = [fileURL path];
        
        NSFileManager *fileManager = [NSFileManager defaultManager];
        NSError *error = nil;
        
        if ([fileManager fileExistsAtPath:fileSystemPath isDirectory:nil]) {
            [fileManager removeItemAtPath:fileSystemPath error:&error];
        }
        
        if (error) {
            NSLog(@"Error deleting image at path: %@", fileSystemPath);
        }
    }
}

+ (void)cleanupCache {
    NSFileManager *fileManager = [NSFileManager defaultManager];
    NSString *cacheDirectory = [NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, YES) firstObject];
    
    for (NSString *imagePath in completedImagePaths) {
        NSString *absoluteImagePath = [cacheDirectory stringByAppendingPathComponent:imagePath];
        NSError *error = nil;
        
        if ([fileManager fileExistsAtPath:absoluteImagePath]) {
            [fileManager removeItemAtPath:absoluteImagePath error:&error];
        }
        
        if (error) {
            NSLog(@"Error deleting image at path: %@", absoluteImagePath);
        }
    }
    
    [completedImagePaths removeAllObjects];
}

@end
