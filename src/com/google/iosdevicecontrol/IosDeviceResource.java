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

/** A resource belonging to an iOS device. */
public abstract class IosDeviceResource implements AutoCloseable {
  private final IosDevice device;

  protected IosDeviceResource(IosDevice device) {
    this.device = checkNotNull(device);
  }

  /** Returns the device to which the resource belongs. */
  public final IosDevice device() {
    return device;
  }

  @Override
  public abstract void close() throws IosDeviceException;
}
