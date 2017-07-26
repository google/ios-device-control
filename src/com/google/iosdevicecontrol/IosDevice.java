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

import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;

/**
 * An iOS device. This device is not necessarily connected to any device host. All implementations
 * must ensure that two devices are equal if and only if their udids are equal.
 */
public interface IosDevice {
  /** Returns the udid of the device. */
  String udid();

  /** Returns whether the device is responsive to communication from the host. */
  boolean isResponsive();

  /** Returns whether the device is currently restarting. */
  boolean isRestarting();

  /**
   * Returns the model of the device.
   *
   * @throws IosDeviceException - if there is an error communicating with the device
   */
  IosModel model() throws IosDeviceException;

  /**
   * Returns the version of the device.
   *
   * @throws IosDeviceException - if there is an error communicating with the device
   */
  IosVersion version() throws IosDeviceException;

  /**
   * Lists the application information of all installed applications on the device.
   *
   * @throws IosDeviceException - if there is an error communicating with the device
   */
  ImmutableSet<IosAppInfo> listApplications() throws IosDeviceException;

  /**
   * Returns whether an application with the specified bundle ID is installed.
   *
   * @throws IosDeviceException - if there is an error communicating with the device
   */
  boolean isApplicationInstalled(IosAppBundleId bundleId) throws IosDeviceException;

  /**
   * Installs the specified application from a .app folder or .ipa archive on the device. If an
   * application with the same bundle ID is already installed, it will be overwritten.
   *
   * @throws IosDeviceException - if there is an error communicating with the device
   */
  void installApplication(Path ipaOrAppPath) throws IosDeviceException;

  /**
   * Uninstall the application with the specified bundle ID on the device. If an application with
   * this bundle ID is not installed, this is a noop.
   *
   * @throws IosDeviceException - if there is an error communicating with the device
   */
  void uninstallApplication(IosAppBundleId bundleId) throws IosDeviceException;

  /**
   * Runs the specified iOS application with the specified args and returns immediately with a
   * {@link IosAppProcess}, which is a future to the text output of the running application.
   *
   * @throws IosDeviceException - if there is an error communicating with the device
   */
  IosAppProcess runApplication(IosAppBundleId bundleId, String... args) throws IosDeviceException;

  /**
   * Starts capturing the system log for the device and writes it to the provided path. Returns an
   * {@link IosDeviceResource}, which can be used in a try-with-resources block.
   *
   * @throws IosDeviceException - if there was an error communicating with the device
   * @throws IllegalStateException - if system log capturing is already started
   */
  IosDeviceResource startSystemLogger(Path logPath) throws IosDeviceException;

  /**
   * Copies the crash logs to the specified directory and removes them from the device.
   *
   * @throws IosDeviceException - if there was an error communicating with the device
   */
  void pullCrashLogs(Path directory) throws IosDeviceException;

  /**
   * Clears the crash logs on the device.
   *
   * @throws IosDeviceException - if there was an error communicating with the device
   */
  void clearCrashLogs() throws IosDeviceException;

  /**
   * Restarts the device and waits for it to be responsive.
   *
   * @throws IosDeviceException - if there is an error communicating with the device
   */
  void restart() throws IosDeviceException;

  /**
   * Takes a screenshot in PNG format and returns the bytes.
   *
   * @throws IosDeviceException - if there is an error communicating with the device
   */
  byte[] takeScreenshot() throws IosDeviceException;
}
