#import <React/RCTEventEmitter.h>

#ifdef RCT_NEW_ARCH_ENABLED
#import "RNCompressorSpec.h"

@interface Compressor : RCTEventEmitter <NativeCompressorSpec>
#else
#import <React/RCTBridgeModule.h>

@interface Compressor : RCTEventEmitter <RCTBridgeModule>
#endif

@end
