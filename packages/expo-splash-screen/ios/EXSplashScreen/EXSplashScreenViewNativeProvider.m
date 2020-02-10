// Copyright © 2018 650 Industries. All rights reserved.

#import <EXSplashScreen/EXSplashScreenViewNativeProvider.h>

@implementation EXSplashScreenViewNativeProvider

- (nonnull UIView *)createSplashScreenView:(EXSplashScreenImageResizeMode)resizeMode {
  UIStoryboard *storyboard = [UIStoryboard storyboardWithName:@"SplashScreen" bundle:[NSBundle mainBundle]];
  UIViewController *splashScreenViewController = [storyboard instantiateViewControllerWithIdentifier:@"SplashScreenViewController"];
  UIView *splashScreenView = splashScreenViewController.view;
  return splashScreenView;
}

@end
