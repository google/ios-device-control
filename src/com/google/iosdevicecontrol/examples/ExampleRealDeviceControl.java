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

package com.google.iosdevicecontrol.examples;

import com.google.iosdevicecontrol.real.RealDevice;
import com.google.iosdevicecontrol.real.RealDeviceHost;
import com.google.iosdevicecontrol.util.FluentLogger;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * This class contains a real device use case for the IosDeviceControl library to document how the
 * library should be used and to verify that the open source build can compile and run successfully.
 */
public class ExampleRealDeviceControl {
  public static void main(String[] args) throws Exception {
    FluentLogger logger = FluentLogger.forEnclosingClass();
    // Initialize the device host by providing disk images.
    RealDeviceHost.Configuration config = RealDeviceHost.withDeveloperDiskImagesFromXcode();
    // Set a supervision identity for more control over the device.
    // config = config.withSupervisionIdentity(Paths.get("path/to/cert"), Paths.get("path/to/key"));
    RealDeviceHost realHost = config.initialize();

    // Pick an arbitrary device
    RealDevice device = (RealDevice) realHost.connectedDevices().iterator().next();
    // Or specify one by udid
    // RealDevice device = (RealDevice) realHost.connectedDevice(udid);

    // Pull some system logs then restart the device.
    Path logPath = Files.createTempDirectory("logs");
    try (AutoCloseable systemLogger = device.startSystemLogger(logPath.resolve("sys.log"))) {}
    device.restart();
    logger.atInfo().log("System logs written to: %s", logPath);
  }
}
