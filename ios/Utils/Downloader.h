#import <Foundation/Foundation.h>

typedef void (^DownloadCompleteCallback)(NSString *filePath);
typedef void (^ErrorCallback)(NSError*);
typedef void (^ProgressCallback)(NSNumber*);

@interface Downloader : NSObject <NSURLSessionDelegate, NSURLSessionDownloadDelegate>

@property (nonatomic, copy) ProgressCallback globalProgressCallback;
@property (nonatomic, copy) DownloadCompleteCallback globalDownloadCompleteCallback;
@property (nonatomic, copy) ErrorCallback globalErrorCallback;

- (NSString *)downloadFile:(NSString *)fromUrl downloadCompleteCallback:(DownloadCompleteCallback)downloadCompleteCallback progressCallback:(ProgressCallback)progressCallback errorCallback:(ErrorCallback)errorCallback;
- (NSString *)generateCacheFilePath:(NSString *)extension;

@end
