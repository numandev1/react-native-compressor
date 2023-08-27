#import "Downloader.h"

@implementation Downloader

NSURLSession* _session;
NSURLSessionDownloadTask* _task;
NSNumber* _statusCode;
NSTimeInterval _lastProgressEmitTimestamp;
NSNumber* _lastProgressValue;
NSNumber* _contentLength;
NSNumber* _bytesWritten;
NSData* _resumeData;
NSString* _toFile;


ProgressCallback globalProgressCallback;
DownloadCompleteCallback globalDownloadCompleteCallback;
ErrorCallback globalErrorCallback;

NSFileHandle* _fileHandle;

- (NSString *)generateCacheFilePath:(NSString *)extension{
    NSUUID *uuid = [NSUUID UUID];
    NSString *imageNameWihtoutExtension = [uuid UUIDString];
    NSString *imageName=[imageNameWihtoutExtension stringByAppendingPathExtension:extension];
    NSString *filePath =
        [NSTemporaryDirectory() stringByAppendingPathComponent:imageName];
    return filePath;
}

- (NSString *)downloadFile:(NSString *)fromUrl downloadCompleteCallback:(DownloadCompleteCallback)downloadCompleteCallback progressCallback:(ProgressCallback)progressCallback errorCallback:(ErrorCallback)errorCallback
{
    NSString *uuid = nil;

    Downloader *downloader = [[Downloader alloc] init];
    globalProgressCallback = progressCallback;
    globalDownloadCompleteCallback = downloadCompleteCallback;
    globalErrorCallback = errorCallback;

  _lastProgressEmitTimestamp = 0;
  _bytesWritten = 0;

    NSURL* url = [NSURL URLWithString:fromUrl];
    NSString *fileExtension = [fromUrl pathExtension];
    
    NSString *toFile =[self generateCacheFilePath:fileExtension];
    _toFile=toFile;

  if ([[NSFileManager defaultManager] fileExistsAtPath:toFile]) {
    _fileHandle = [NSFileHandle fileHandleForWritingAtPath:toFile];

    if (!_fileHandle) {
      NSError* error = [NSError errorWithDomain:@"Downloader" code:NSURLErrorFileDoesNotExist
                                userInfo:@{NSLocalizedDescriptionKey: [NSString stringWithFormat: @"Failed to write target file at path: %@", toFile]}];

        globalErrorCallback(error);
      return nil;
    } else {
      [_fileHandle closeFile];
    }
  }

  NSURLSessionConfiguration *config;

  config = [NSURLSessionConfiguration defaultSessionConfiguration];

  _session = [NSURLSession sessionWithConfiguration:config delegate:downloader delegateQueue:nil];
  _task = [_session downloadTaskWithURL:url];
  [_task resume];

    return uuid;
}

- (void)URLSession:(NSURLSession *)session downloadTask:(NSURLSessionDownloadTask *)downloadTask didWriteData:(int64_t)bytesWritten totalBytesWritten:(int64_t)totalBytesWritten totalBytesExpectedToWrite:(int64_t)totalBytesExpectedToWrite
{
  NSHTTPURLResponse *httpResponse = (NSHTTPURLResponse *)downloadTask.response;
 
    _statusCode = [NSNumber numberWithLong:httpResponse.statusCode];
    _contentLength = [NSNumber numberWithLong:httpResponse.expectedContentLength];

    if ([_statusCode isEqualToNumber:[NSNumber numberWithInt:200]]) {

    _bytesWritten = @(totalBytesWritten);

  
      double doubleBytesWritten = (double)[_bytesWritten longValue];
      double doubleContentLength = (double)[_contentLength longValue];
      double doublePercents = doubleBytesWritten / doubleContentLength * 100;
      NSNumber* progress = [NSNumber numberWithUnsignedInt: floor(doublePercents)];
        if ([progress unsignedIntValue] % 10 == 0) {
            if (([progress unsignedIntValue] != [_lastProgressValue unsignedIntValue]) || ([_bytesWritten unsignedIntegerValue] == [_contentLength longValue])) {
                _lastProgressValue = [NSNumber numberWithUnsignedInt:[progress unsignedIntValue]];
                NSNumber *progressPercentage = @([_bytesWritten doubleValue] / [_contentLength doubleValue]);
                return globalProgressCallback(progressPercentage);
            }
        }
      
    
  }
}

- (void)URLSession:(NSURLSession *)session downloadTask:(NSURLSessionDownloadTask *)downloadTask didFinishDownloadingToURL:(NSURL *)location
{
  NSHTTPURLResponse *httpResponse = (NSHTTPURLResponse *)downloadTask.response;
  if (!_statusCode) {
    _statusCode = [NSNumber numberWithLong:httpResponse.statusCode];
  }
  NSURL *destURL = [NSURL fileURLWithPath:_toFile];
  NSFileManager *fm = [NSFileManager defaultManager];
  NSError *error = nil;
  if([_statusCode integerValue] >= 200 && [_statusCode integerValue] < 300) {
    [fm removeItemAtURL:destURL error:nil];       // Remove file at destination path, if it exists
    [fm moveItemAtURL:location toURL:destURL error:&error];
    // There are no guarantees about how often URLSession:downloadTask:didWriteData: will fire,
    // so we read an authoritative number of bytes written here.
    _bytesWritten = @([fm attributesOfItemAtPath:_toFile error:nil].fileSize);
  }
  if (error) {
    NSLog(@"RNFS download: unable to move tempfile to destination. %@, %@", error, error.userInfo);
  }

  // When numerous downloads are called the sessions are not always invalidated and cleared by iOS14. 
  // This leads to error 28 – no space left on device so we manually flush and invalidate to free up space
  if(session != nil){
    [session flushWithCompletionHandler:^{
      [session finishTasksAndInvalidate];
    }];
  }
    NSURL *fileURL = [NSURL fileURLWithPath:_toFile];
  return globalDownloadCompleteCallback([fileURL absoluteString]);
}

- (void)URLSession:(NSURLSession *)session task:(NSURLSessionTask *)task didCompleteWithError:(NSError *)error
{
  if (error) {
    NSLog(@"RNFS download: didCompleteWithError %@, %@", error, error.userInfo);
    if (error.code != NSURLErrorCancelled) {
      _resumeData = error.userInfo[NSURLSessionDownloadTaskResumeData];
      if (_resumeData != nil) {
       
      } else {
          globalErrorCallback(error);
      }
    }
  }
}



@end
