// Copyright 2017 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#import <SystemConfiguration/CaptiveNetwork.h>
#import <arpa/inet.h>
#import <ifaddrs.h>
#import <netdb.h>

#import "third_party/objective_c/iosdevicecontrol/openURL/OpenURLAppDelegate.h"

#if RUN_KIF_TESTS
#import "third_party/objective_c/iosdevicecontrol/openURL/OpenUrlKIFTestController.h"
#endif

@implementation OpenURLAppDelegate

#if RUN_KIF_TESTS

- (BOOL)application:(UIApplication *)application
    didFinishLaunchingWithOptions:(NSDictionary *)launchOptions {
  // Must have a valid window in order to run a KIF test.
  self.window = [[UIWindow alloc] initWithFrame:[[UIScreen mainScreen] bounds]];
  self.window.rootViewController = [[UIViewController alloc] initWithNibName:nil bundle:nil];
  [self.window makeKeyAndVisible];

  [[OpenUrlKIFTestController sharedInstance] startTestingWithCompletionBlock:^{
    exit((int)[[OpenUrlKIFTestController sharedInstance] failureCount]);
  }];
  return YES;
}

#else

- (void)applicationDidBecomeActive:(UIApplication *)application {
  // To pass args in Xcode, see Product > Scheme > Edit Scheme > Arguments.
  // For idevice-app-runner use --args flag.
  NSArray* args = [[NSProcessInfo processInfo] arguments];
  BOOL sendToBackground = NO;
  if ([args count] < 2) {
    println(@"Usage: idevice-app-runner -s com.google.openURL --args [--check_wifi | URL]");
  } else {
    NSString *arg = [args objectAtIndex:1];
    if ([arg isEqualToString:@"--check_wifi"]) {
      println(@"Getting WiFi information");
      NSString *address = [[self class] getWiFiIpAddress];
      if (address) {
        println(@"WiFi is enabled at %@", address);
        NSString *ssid = [[self class] getWiFiSsid];
        if (ssid) {
          println(@"WiFi SSID is %@", ssid);
        } else {
          println(@"Unable to get WiFi SSID");
        }
      } else {
        println(@"WiFi is disabled");
      }
    } else {
      NSString *urlString = arg;
      println(@"Trying to open %@ ...", urlString);
      sendToBackground = [[self class] openUrlWithString:urlString];
      println(@"%@ URL.", sendToBackground ? @"Opened" : @"Unable to open");
    }
  }

  // If Safari did not open, this app will never enter the background,
  // so schedule a quit to happen immediately after the function returns.
  // If we quit within this function, the debugserver may restart the app.
  if (!sendToBackground) {
    [[self class] performSelectorOnMainThread:@selector(quit) withObject:nil waitUntilDone:NO];
  }
}

#endif

- (void)applicationDidEnterBackground:(UIApplication *)application {
#if !RUN_KIF_TESTS
  // The app will enter the background once Safari is launched.
  // Give Safari a couple seconds to open the page and then quit.
  sleep(2);
  [[self class] quit];
#endif
}

+ (void)quit {
  // The exit code is immaterial, since the debugserver doesn't report it back.
  exit(0);
}

+ (NSString *)getWiFiIpAddress {
  // http://stackoverflow.com/questions/6807788/how-to-get-ip-address-of-iphone-programatically
  // returns the IP address of the device. If the device is offline, it returns NULL.
  NSString *address = NULL;
  struct ifaddrs *interfaces = NULL;
  struct ifaddrs *interface_address = NULL;

  // Retrieve the current interfaces - returns 0 on success
  if (getifaddrs(&interfaces) == 0) {
    interface_address = interfaces;
    while (interface_address != NULL) {
      if (interface_address->ifa_addr->sa_family == AF_INET &&
          [[NSString stringWithUTF8String:interface_address->ifa_name]
              isEqualToString:@"en0"]) {
        address = [NSString stringWithUTF8String:inet_ntoa(
            ((struct sockaddr_in *)interface_address->ifa_addr)->sin_addr)];
        break;
      }
      interface_address = interface_address->ifa_next;
    }
    freeifaddrs(interfaces);
  }
  return address;
}

+ (NSString *)getWiFiSsid {
  NSArray *interfaces = CFBridgingRelease(CNCopySupportedInterfaces());
  for (NSString *interface in interfaces) {
    id info = CFBridgingRelease(CNCopyCurrentNetworkInfo((__bridge CFStringRef)(interface)));
    NSString *networkSSID = [info valueForKey:@"SSID"];
    if (networkSSID) {
      return networkSSID;
    }
  }
  return nil;
}

+ (BOOL)openUrlWithString:(NSString *)urlString {
  NSURL *url = [NSURL URLWithString:urlString];
  BOOL canOpen = [[UIApplication sharedApplication] canOpenURL:url];
  if (canOpen) {
    // For unknown reasons, openURL always returns NO on iOS7.
    // So we ignore the return value and count on canOpenURL instead.
    [[UIApplication sharedApplication] openURL:url];
  }
  return canOpen;
}

@end

void println(NSString *format, ...) {
  va_list args;
  va_start(args, format);
  NSString *s = [[NSString alloc] initWithFormat:format arguments:args];
  va_end(args);
  printf("%s\n", [s UTF8String]);
}
