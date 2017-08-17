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
import javax.json.JsonObject;

/**
 * An applicationSentData message.
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
 *   <string>_rpc_applicationSentData:</string>
 *   <key>__argument</key>
 *   <dict>
 *     <key>WIRApplicationIdentifierKey</key>
 *     <string>com.apple.mobilesafari</string>
 *     <key>WIRDestinationKey</key>
 *     <string>C1EAD225-D6BC-44B9-9089-2D7CC2D2204C</string>
 *     <key>WIRMessageDataKey</key>
 *     <data>}{"id": 1, "result": true}{@code</data>
 *   </dict>
 * </dict>
 * </plist>
 * }</pre>
 */
@AutoValue
public abstract class ApplicationSentDataMessage extends InspectorMessage {
  /** Returns a new builder. */
  public static Builder builder() {
    return new AutoValue_ApplicationSentDataMessage.Builder();
  }

  /** A builder for creating applicationSentData messages. */
  @AutoValue.Builder
  public abstract static class Builder extends InspectorMessage.Builder {
    @Override
    public abstract Builder applicationId(String applicationId);

    @Override
    public abstract Builder destination(String destination);

    @Override
    public abstract Builder messageData(JsonObject messageData);

    @Override
    public abstract ApplicationSentDataMessage build();
  }

  @Override
  public abstract String applicationId();

  @Override
  public abstract String destination();

  @Override
  public abstract JsonObject messageData();

  @Override
  public MessageSelector selector() {
    return MessageSelector.APPLICATION_SENT_DATA;
  }
}
