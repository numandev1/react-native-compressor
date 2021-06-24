#import <UIKit/UIKit.h>

typedef NS_ENUM(long, OutputType) { jpg,
                                    png
};

typedef NS_ENUM(long, InputType) { base64,
                                   uri
};

typedef NS_ENUM(long, ReturnableOutputType) { rbase64,
    ruri
};

@interface ImageCompressorOptions : NSObject
+ (ImageCompressorOptions *)fromDictionary:(NSDictionary *)dictionary;
+ (NSString *) getOutputInString:(enum OutputType*)output;

@property(nonatomic, assign) Boolean autoCompress;
@property(nonatomic, assign) int maxWidth;
@property(nonatomic, assign) int maxHeight;
@property(nonatomic, assign) float quality;
@property(nonatomic, assign) enum OutputType output;
@property(nonatomic, assign) enum InputType input;
@property(nonatomic, assign) enum ReturnableOutputType returnableOutputType;

@end
