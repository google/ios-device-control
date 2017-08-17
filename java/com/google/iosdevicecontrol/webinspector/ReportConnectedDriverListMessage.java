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
import com.google.common.collect.ImmutableList;
import java.util.List;

/**
 * A reportConnectedDriverList message.
 *
 * <p>NOTE: As of iOS10, Safari issues reportConnectedDriverList messages, but so far they have only
 * contained empty dictionaries. Presumably they will eventually contain a list of "drivers" but
 * what these look like remains to be seen. Until then, the InspectorDriver is just a placeholder.
 *
 * <p>Example:
 *
 * <pre>{@code
 * <?xml version="1.0" encoding="UTF-8"?>
 * <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN"
 *     "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
 * <plist version="1.0">
 * <dict>
 *   <key>__selector</key>
 *   <string>_rpc_reportConnectedDriverList:</string>
 *   <key>__argument</key>
 *   <dict>
 *     <key>WIRDriverDictionaryKey</key>
 *     <dict></dict>
 *   </dict>
 * </dict>
 * </plist>
 * }</pre>
 */
@AutoValue
public abstract class ReportConnectedDriverListMessage extends InspectorMessage {
  /** Returns a new builder. */
  public static Builder builder() {
    return new AutoValue_ReportConnectedDriverListMessage.Builder();
  }

  /** A builder for creating reportConnectedDriverList messages. */
  @AutoValue.Builder
  public abstract static class Builder extends InspectorMessage.Builder {
    @Override
    public abstract Builder driverDictionary(List<InspectorDriver> driverDictionary);

    @Override
    public abstract ReportConnectedDriverListMessage build();
  }

  @Override
  public abstract ImmutableList<InspectorDriver> driverDictionary();

  @Override
  public MessageSelector selector() {
    return MessageSelector.REPORT_CONNECTED_DRIVER_LIST;
  }
}
