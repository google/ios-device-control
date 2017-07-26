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

import com.google.auto.value.AutoValue;

/** A configuration profile for a real iOS device. */
@AutoValue
public abstract class ConfigurationProfile {
  public static Builder builder() {
    return new AutoValue_ConfigurationProfile.Builder();
  }

  public abstract String displayName();

  public abstract String identifier();

  public abstract int version();

  /** A builder for a configuration profile. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder displayName(String displayName);

    public abstract Builder identifier(String identitifer);

    public abstract Builder version(int version);

    public abstract ConfigurationProfile build();
  }
}
