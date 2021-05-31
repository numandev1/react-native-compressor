#import <React/RCTBridgeModule.h>
#import <React/RCTEventEmitter.h>

@interface VideoUpload : NSObject <RCTBridgeModule>

+ (void)compress:(NSString *)fileUrl;
+ (void)upload:(NSString *)fileUrl;
+ (void)activateBackgroundTask: (NSDictionary *)options;
+ (void)deactivateBackgroundTask: (NSDictionary *)options;

@end
