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

import com.google.common.base.Optional;
import javax.annotation.Nullable;

/**
 * Signals an error occurred when interacting with an iOS device. Each {@code IosDeviceException}
 * optionally provides a suggested {@link Remedy} that <i>may</i> resolve the error.
 */
public class IosDeviceException extends Exception {
  /** Remedies to some kinds of iOS device errors. */
  public enum Remedy {
    DISMISS_DIALOG,
    REINSTALL_APP,
    RESTART_DEVICE,
  }

  private final IosDevice device;
  private final Optional<Remedy> remedy;

  public IosDeviceException(IosDevice device, String message) {
    this(device, device + ": " + message, null, Optional.<Remedy>absent());
  }

  public IosDeviceException(IosDevice device, Throwable cause) {
    this(device, device.toString(), checkNotNull(cause), Optional.<Remedy>absent());
  }

  public IosDeviceException(IosDevice device, Throwable cause, Remedy remedy) {
    this(device, device.toString(), checkNotNull(cause), Optional.of(remedy));
  }

  private IosDeviceException(
      IosDevice device, String message, @Nullable Throwable cause, Optional<Remedy> remedy) {
    super(message, cause);
    this.device = checkNotNull(device);
    this.remedy = remedy;
  }

  /** Returns the device on which the error occurred. */
  public final IosDevice device() {
    return device;
  }

  /** Suggested remedy that <i>may</i> resolve the cause of the error. */
  public final Optional<Remedy> remedy() {
    return remedy;
  }
}
