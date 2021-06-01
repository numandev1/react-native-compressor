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
        
        if ([key isEqual:@"maxWidth"]) {
            options.maxWidth = [value intValue];
        }else if ([key isEqual:@"maxHeight"]) {
            options.maxHeight = [value intValue];
        } else if ([key isEqual:@"quality"]) {
            options.quality = [value floatValue];
        } else if ([key isEqual:@"input"]) {
            [options parseInput: value];
        } else if ([key isEqual:@"output"]) {
            [options parseOutput: value];
        }
    }
    
    return options;
}

- (instancetype) init {
    self = [super init];
    
    if (self) {
        self.maxWidth = 640;
        self.maxHeight = 480;
        self.quality = 1.0f;
        self.input = base64;
        self.output = jpg;
    }
    
    return self;
}

@synthesize maxWidth;
@synthesize maxHeight;
@synthesize quality;
@synthesize input;
@synthesize output;

- (void) parseInput:(NSString*)input {
    NSDictionary *inputTranslations = @{ @"base64": @(base64), @"uri": @(uri) };
    NSNumber *enumValue = [inputTranslations objectForKey: input];
    
    self.input = [enumValue longValue];
}

- (void) parseOutput:(NSString*)output {
    NSDictionary *outputTranslations = @{ @"jpg": @(jpg), @"png": @(png) };
    NSNumber *enumValue = [outputTranslations objectForKey: output];
    
    self.output = [enumValue longValue];
}

@end
