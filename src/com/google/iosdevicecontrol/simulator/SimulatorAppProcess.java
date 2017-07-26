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

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import com.google.iosdevicecontrol.command.CommandFailureException;
import com.google.iosdevicecontrol.command.CommandProcess;
import com.google.iosdevicecontrol.IosAppProcess;
import com.google.iosdevicecontrol.IosDevice;
import com.google.iosdevicecontrol.IosDeviceException;
import java.io.Reader;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

/** Implementation of {@link IosAppProcess} backed by an `xcrun simctl` command process. */
final class SimulatorAppProcess implements IosAppProcess {
  private final IosDevice device;
  private final CommandProcess process;

  SimulatorAppProcess(IosDevice device, CommandProcess process) {
    this.device = checkNotNull(device);
    this.process = checkNotNull(process);
  }

  @Override
  public SimulatorAppProcess kill() {
    process.kill();
    return this;
  }

  @Override
  public String await() throws IosDeviceException, InterruptedException {
    try {
      return process.await().stderrStringUtf8();
    } catch (CommandFailureException e) {
      throw new IosDeviceException(device, e);
    }
  }

  @Override
  public String await(Duration timeout)
      throws IosDeviceException, InterruptedException, TimeoutException {
    try {
      return process.await(timeout.getNano(), NANOSECONDS).stderrStringUtf8();
    } catch (CommandFailureException e) {
      throw new IosDeviceException(device, e);
    }
  }

  @Override
  public Reader outputReader() {
    return process.stderrReaderUtf8();
  }
}
