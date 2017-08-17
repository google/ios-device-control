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

import com.google.iosdevicecontrol.IosAppBundleId;
import com.google.iosdevicecontrol.IosAppProcess;
import com.google.iosdevicecontrol.simulator.SimulatorDevice;
import com.google.iosdevicecontrol.simulator.SimulatorDeviceHost;
import com.google.iosdevicecontrol.util.FluentLogger;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * This class contains a simulator use case for the IosDeviceControl library to document how the
 * library should be used and to verify that the open source build can compile and run successfully.
 */
public class ExampleSimulatorDeviceControl {
  public static void main(String[] args) throws Exception {
    FluentLogger logger = FluentLogger.forEnclosingClass();
    SimulatorDeviceHost simHost = SimulatorDeviceHost.INSTANCE;

    // Optionally shutdown all devices before starting one.
    simHost.shutdownAllDevices();

    // Pick an arbitrary device.
    SimulatorDevice sim = (SimulatorDevice) simHost.connectedDevices().iterator().next();
    // Or specify one by udid
    // SimulatorDevice sim = (SimulatorDevice) simHost.connectedDevice(udid);

    // Start the device before interacting with it.
    sim.startup();

    // The device can now be interacted with. Here is an example of starting Safari, taking a
    // screenshot, then closing Safari
    IosAppProcess safariProcess = sim.runApplication(new IosAppBundleId("com.apple.mobilesafari"));
    byte[] screenshotBytes = sim.takeScreenshot();
    Path screenshotPath = Files.createTempFile("screenshot", ".png");
    Files.write(screenshotPath, screenshotBytes);
    safariProcess.kill();
    logger.atInfo().log("Screenshot written to: %s", screenshotPath);
  }
}
