#import <Foundation/Foundation.h>
#import <UIKit/UIKit.h>

#import "ImageCompressorOptions.h"

@implementation ImageCompressorOptions
+ (ImageCompressorOptions*) fromDictionary:(NSDictionary *)dictionary {
    ImageCompressorOptions *options = [[ImageCompressorOptions alloc] init];
    
    if (dictionary == nil) {
        return options;
    }
    
    for (id key in dictionary) {
        id value = [dictionary objectForKey:key];
        
        if ([key isEqual:@"autoCompress"]) {
            options.autoCompress = [value boolValue];
        }else if ([key isEqual:@"maxWidth"]) {
            options.maxWidth = [value intValue];
        }else if ([key isEqual:@"maxHeight"]) {
            options.maxHeight = [value intValue];
        } else if ([key isEqual:@"quality"]) {
            options.quality = [value floatValue];
        } else if ([key isEqual:@"input"]) {
            [options parseInput: value];
        } else if ([key isEqual:@"output"]) {
            [options parseOutput: value];
        } else if ([key isEqual:@"returnableOutputType"]) {
            [options parseReturnableOutput: value];
        }
    }
    
    return options;
}

+ (NSString *) getOutputInString:(enum OutputType*)output{
    if(output==jpg)
   {
       return  @"jpg";
   }
    else
    {
        return @"png";
    }
}

- (instancetype) init {
    self = [super init];
    
    if (self) {
        self.autoCompress = false;
        self.maxWidth = 1280;
        self.maxHeight = 1280;
        self.quality = 0.8f;
        self.input = uri;
        self.output = jpg;
        self.returnableOutputType = uri;
    }
    
    return self;
}

@synthesize autoCompress;
@synthesize maxWidth;
@synthesize maxHeight;
@synthesize quality;
@synthesize input;
@synthesize output;
@synthesize returnableOutputType;

- (void) parseInput:(NSString*)input {
    NSDictionary *inputTranslations = @{ @"base64": @(base64), @"uri": @(uri) };
    NSNumber *enumValue = [inputTranslations objectForKey: input];
    
    self.input = [enumValue longValue];
}

- (void) parseReturnableOutput:(NSString*)input {
    NSDictionary *inputTranslations = @{ @"base64": @(rbase64), @"uri": @(ruri) };
    NSNumber *enumValue = [inputTranslations objectForKey: input];
    
    self.returnableOutputType = [enumValue longValue];
}

- (void) parseOutput:(NSString*)output {
    NSDictionary *outputTranslations = @{ @"jpg": @(jpg), @"png": @(png) };
    NSNumber *enumValue = [outputTranslations objectForKey: output];
    
    self.output = [enumValue longValue];
}

@end
