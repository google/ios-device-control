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
 * An applicationSentListing message.
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
 *   <string>_rpc_applicationSentListing:</string>
 *   <key>__argument</key>
 *   <dict>
 *     <key>WIRApplicationIdentifierKey</key>
 *     <string>com.apple.mobilesafari</string>
 *     <key>WIRListingKey</key>
 *     <dict>
 *       <key>1</key>
 *       <dict>
 *         <key>WIRPageIdentifierKey</key>
 *         <integer>1</integer>
 *         <key>WIRTitleKey</key>
 *         <string>Google</string>
 *         <key>WIRURLKey</key>
 *         <string>http://www.google.com</string>
 *       </dict>
 *       <key>2</key>
 *       <dict>
 *         <key>WIRPageIdentifierKey</key>
 *         <integer>2</integer>
 *         <key>WIRTitleKey</key>
 *         <string>Yahoo</string>
 *         <key>WIRURLKey</key>
 *         <string>http://www.yahoo.com</string>
 *       </dict>
 *     </dict>
 *   </dict>
 * </dict>
 * </plist>
 * }</pre>
 */
@AutoValue
public abstract class ApplicationSentListingMessage extends InspectorMessage {
  /** Returns a new builder. */
  public static Builder builder() {
    return new AutoValue_ApplicationSentListingMessage.Builder();
  }

  /** A builder for creating applicationSentListing messages. */
  @AutoValue.Builder
  public abstract static class Builder extends InspectorMessage.Builder {
    @Override
    public abstract Builder applicationId(String applicationId);

    @Override
    public abstract Builder listing(List<InspectorPage> listing);

    @Override
    public abstract ApplicationSentListingMessage build();
  }

  @Override
  public abstract String applicationId();

  @Override
  public abstract ImmutableList<InspectorPage> listing();

  @Override
  public MessageSelector selector() {
    return MessageSelector.APPLICATION_SENT_LISTING;
  }
}
