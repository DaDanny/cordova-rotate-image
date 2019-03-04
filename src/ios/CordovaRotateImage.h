#import <Cordova/CDV.h>
#import <UIKit/UIKit.h>
#import <Photos/Photos.h>

@interface CordovaRotateImage : CDVPlugin{
    CDVInvokedUrlCommand *rotateImageCommand;
}

-(void) correctOrientation:(CDVInvokedUrlCommand *)command;


@end
