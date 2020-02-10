// Copyright 2016-present 650 Industries. All rights reserved.

#import <Foundation/Foundation.h>
#import <UMCore/UMSingletonModule.h>
#import <EXSplashScreen/EXSplashScreenController.h>
#import <EXSplashScreen/EXSplashScreenViewProvider.h>

NS_ASSUME_NONNULL_BEGIN

@interface EXSplashScreen : UMSingletonModule

+ (instancetype)sharedInstance;

/**
 * Overloded method.
 * See main method below.
 */
- (void)show:(UIViewController *)viewController resizeMode:(EXSplashScreenImageResizeMode)resizeMode;

/**
 * Entry point for SplashScreen unimodule.
 * Registers SplashScreen for given viewController and presents it in it.
 */
- (void)show:(UIViewController *)viewController resizeMode:(EXSplashScreenImageResizeMode)resizeMode
                                  splashScreenViewProvider:(id<EXSplashScreenViewProvider>)splashScreenViewProvider
                                           successCallback:(void (^)(void))successCallback
                                           failureCallback:(void (^)(NSString *message))failureCallback;

/**
 * Hides SplashScreen for given viewController.
 */
- (void)hide:(UIViewController *)viewController successCallback:(void (^)(void))successCallback
                                                failureCallback:(void (^)(NSString *message))failureCallback;

/**
 * Prevents SplshScreen from default autohiding.
 */
- (void)preventAutoHide:(UIViewController *)viewController
        successCallback:(void (^)(void))successCallback
        failureCallback:(void (^)(NSString *message))failureCallback;

/**
 * Signaling method that has to be called upon Content is rendered in View Hierarchy.
 * Without calling it auothind would not work.
 */
- (void)onAppContentDidAppear:(UIViewController *)viewController;

/**
 * Singlaing method that is responsible for reshowing SplashScreen upon full content reload.
 */
- (void)onAppContentWillReload:(UIViewController *)viewController;

@end

NS_ASSUME_NONNULL_END
