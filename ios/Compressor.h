
#ifdef RCT_NEW_ARCH_ENABLED
#import "RNCompressorSpec.h"

@interface Compressor : NSObject <NativeCompressorSpec>
#else
#import <React/RCTBridgeModule.h>

@interface Compressor : NSObject <RCTBridgeModule>
#endif

@end
