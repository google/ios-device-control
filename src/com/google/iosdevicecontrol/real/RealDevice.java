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

package com.google.iosdevicecontrol.real;

import com.google.common.collect.ImmutableList;
import com.google.iosdevicecontrol.IosDevice;
import com.google.iosdevicecontrol.IosDeviceException;
import java.nio.file.Path;

/** Interface that extends {@link IosDevice} for real device specific commands and operations */
public interface RealDevice extends IosDevice {
  /** Installs a configuration or provisioning profile at the specified path. */
  void installProfile(Path profilePath) throws IosDeviceException;

  /** Removes a configuration profile with the specified identifier. */
  void removeProfile(String identifier) throws IosDeviceException;

  /** Returns a list of the installed configuration profiles. */
  ImmutableList<ConfigurationProfile> listConfigurationProfiles() throws IosDeviceException;

  /**
   * Syncs the device's clock to the system time.
   *
   * @throws IosDeviceException - if there is an error communicating with the device
   */
  void syncToSystemTime() throws IosDeviceException;

  /**
   * Returns the battery level of the device as an integer percentage in the range [0, 100].
   *
   * @throws IosDeviceException - if there is an error communicating with the device
   */
  int batteryLevel() throws IosDeviceException;
}
