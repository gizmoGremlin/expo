// Copyright 2018-present 650 Industries. All rights reserved.

#import <EXSplashScreen/EXSplashScreenModule.h>
#import <EXSplashScreen/EXSplashScreen.h>
#import <UMCore/UMAppLifecycleService.h>
#import <UMCore/UMUtilities.h>

@interface EXSplashScreenModule ()

@property (nonatomic, weak) UMModuleRegistry *moduleRegistry;
@property (nonatomic, weak) id<UMUtilitiesInterface> utilities;

@end

@implementation EXSplashScreenModule

UM_EXPORT_MODULE(ExpoSplashScreen);

- (void)setModuleRegistry:(UMModuleRegistry *)moduleRegistry
{
  _moduleRegistry = moduleRegistry;
  _utilities = [moduleRegistry getModuleImplementingProtocol:@protocol(UMUtilitiesInterface)];
  [[moduleRegistry getModuleImplementingProtocol:@protocol(UMAppLifecycleService)] registerAppLifecycleListener:self];
}

UM_EXPORT_METHOD_AS(hideAsync,
                    hideWithResolve:(UMPromiseResolveBlock)resolve
                    reject:(UMPromiseRejectBlock)reject)
{
  UIViewController* currentViewController = _utilities.currentViewController;
  if (!currentViewController) {
    reject(@"ERR_SPLASH_SCREEN", @"No valid ViewController.", nil);
  }
  [EXSplashScreen.sharedInstance hide:currentViewController
                                 successCallback:^{ resolve(nil); }
                                 failureCallback:^(NSString *message){ reject(@"ERR_SPLASH_SCREEN", message, nil); }];
}

UM_EXPORT_METHOD_AS(preventAutoHideAsync,
                    preventAutoHideWithResolve:(UMPromiseResolveBlock)resolve
                    reject:(UMPromiseRejectBlock)reject)
{
  UIViewController* currentViewController = _utilities.currentViewController;
  if (!currentViewController) {
    reject(@"ERR_SPLASH_SCREEN", @"No valid ViewController.", nil);
  }
  [EXSplashScreen.sharedInstance preventAutoHide:currentViewController
                                 successCallback:^{ resolve(nil); }
                                 failureCallback:^(NSString *message){ reject(@"ERR_SPLASH_SCREEN", message, nil); }];
}

# pragma mark - UMAppLifecycleListener

- (void)onAppBackgrounded {}

- (void)onAppForegrounded {}

- (void)onAppContentDidAppear
{
  UIViewController* currentViewController = _utilities.currentViewController;
  if (currentViewController) {
    [EXSplashScreen.sharedInstance onAppContentDidAppear:currentViewController];
  }
}

- (void)onAppContentWillReload {
  UIViewController* currentViewController = _utilities.currentViewController;
  if (currentViewController) {
    [EXSplashScreen.sharedInstance onAppContentWillReload:currentViewController];
  }
}

@end
