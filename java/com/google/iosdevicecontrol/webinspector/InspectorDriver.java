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

package com.google.iosdevicecontrol.webinspector;

import com.google.auto.value.AutoValue;

/**
 * Information about a driver in an inspector message.
 *
 * <p>NOTE: As of iOS10, Safari issues reportConnectedDriverList messages, but so far they have only
 * contained empty dictionaries. Presumably they will eventually contain a list of "drivers" but
 * what these look like remains to be seen. Until then, the InspectorDriver is just a placeholder.
 */
@AutoValue
public abstract class InspectorDriver extends MessageDict {
  /** Returns a new builder. */
  public static Builder builder() {
    return new AutoValue_InspectorDriver.Builder();
  }

  /** A builder for creating inspector drivers. */
  @AutoValue.Builder
  public abstract static class Builder extends MessageDict.Builder {
    abstract Builder driverId(String driverId);

    @Override
    public abstract InspectorDriver build();
  }

  abstract String driverId();
}
