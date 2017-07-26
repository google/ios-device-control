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

import com.google.auto.value.AutoValue;

/**
 * Version information of an iOS device.
 *
 * @see <a href="https://www.theiphonewiki.com/wiki/IBoot_(Bootloader)">theiphonewiki.com</a>
 */
@AutoValue
public abstract class IosVersion {
  public static Builder builder() {
    return new AutoValue_IosVersion.Builder();
  }

  /** The build version, e.g. "12H321". */
  public abstract String buildVersion();

  /** The product version, e.g. "8.4.1". */
  public abstract String productVersion();

  /** The major version number, e.g. 8. */
  public final int majorVersion() {
    return Integer.parseInt(productVersion().substring(0, productVersion().indexOf('.')));
  }

  public abstract Builder toBuilder();

  /**
   * IosVersion builder.
   */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder buildVersion(String buildVersion);

    public abstract Builder productVersion(String productVersion);

    public abstract IosVersion build();
  }
}
