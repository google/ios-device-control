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

package com.google.iosdevicecontrol;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.util.Optional;

/** A host machine to which iOS devices are connected. */
public interface IosDeviceHost {
  /**
   * Returns the connected device with the given udid.
   *
   * @throws IOException - if unable to retrieve the connected device
   */
  default IosDevice connectedDevice(String udid) throws IOException {
    checkNotNull(udid);
    Optional<IosDevice> device =
        connectedDevices().stream().filter(d -> d.udid().equals(udid)).findAny();
    if (!device.isPresent()) {
      throw new IOException("Device not connected: " + udid);
    }
    return device.get();
  }

  /**
   * Returns all the devices currently connected to the host.
   *
   * @throws IOException - if unable to retrieve the list of connected devices
   */
  ImmutableSet<IosDevice> connectedDevices() throws IOException;
}
