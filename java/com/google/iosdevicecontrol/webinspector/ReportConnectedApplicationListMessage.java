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
 * A reportConnectedApplicationList message.
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
 *   <string>_rpc_reportConnectedApplicationList:</string>
 *   <key>__argument</key>
 *   <dict>
 *     <key>WIRApplicationDictionaryKey</key>
 *     <dict>
 *       <key>PID:176</key>
 *       <dict>
 *         <key>WIRApplicationBundleIdentifierKey</key>
 *         <string>com.apple.mobilesafari</string>
 *         <key>WIRApplicationIdentifierKey</key>
 *         <string>PID:176</string>
 *         <key>WIRApplicationNameKey</key>
 *         <string>Safari</string>
 *         <key>WIRHostApplicationIdentifierKey</key>
 *         <string>PID:457</string>
 *         <key>WIRIsApplicationActiveKey</key>
 *         <integer>1</integer>
 *         <key>WIRIsApplicationProxyKey</key>
 *         <false/>
 *       </dict>
 *       <key>PID:263</key>
 *       <dict>
 *         <key>WIRApplicationBundleIdentifierKey</key>
 *         <string>com.apple.WebKit.WebContent</string>
 *         <key>WIRApplicationIdentifierKey</key>
 *         <string>PID:263</string>
 *         <key>WIRApplicationNameKey</key>
 *         <string>WebContent</string>
 *         <key>WIRHostApplicationIdentifierKey</key>
 *         <string>PID:739</string>
 *         <key>WIRIsApplicationActiveKey</key>
 *         <integer>0</integer>
 *         <key>WIRIsApplicationProxyKey</key>
 *         <true/>
 *       </dict>
 *     </dict>
 *   </dict>
 * </dict>
 * </plist>
 * }</pre>
 */
@AutoValue
public abstract class ReportConnectedApplicationListMessage extends InspectorMessage {
  /** Returns a new builder. */
  public static Builder builder() {
    return new AutoValue_ReportConnectedApplicationListMessage.Builder();
  }

  /** A builder for creating reportConnectedApplicationList messages. */
  @AutoValue.Builder
  public abstract static class Builder extends InspectorMessage.Builder {
    @Override
    public abstract Builder applicationDictionary(List<InspectorApplication> applicationDictionary);

    @Override
    public abstract ReportConnectedApplicationListMessage build();
  }

  @Override
  public abstract ImmutableList<InspectorApplication> applicationDictionary();

  @Override
  public MessageSelector selector() {
    return MessageSelector.REPORT_CONNECTED_APPLICATION_LIST;
  }
}
