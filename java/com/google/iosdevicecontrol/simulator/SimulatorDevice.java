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

package com.google.iosdevicecontrol.simulator;

import com.google.iosdevicecontrol.IosDevice;
import com.google.iosdevicecontrol.IosDeviceException;

/** Interface that extends {@link IosDevice} for Simulator specific commands and operations */
public interface SimulatorDevice extends IosDevice {
  /**
   * Shutdown this device and waits until it has completed the shutdown process.
   *
   * @throws IosDeviceException - if there was an error communicating with the device
   */
  void shutdown() throws IosDeviceException;

  /**
   * Startup the device and wait until it has completed the boot process.
   *
   * @throws IosDeviceException - if there was an error communicating with the device
   */
  void startup() throws IosDeviceException;

  /**
   * Shutdown the device, erase its content, and reset its settings to be factory new.
   *
   * @throws IosDeviceException - if there was an error communicating with the device
   */
  void erase() throws IosDeviceException;
}
