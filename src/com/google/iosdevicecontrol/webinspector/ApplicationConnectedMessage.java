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
 * An applicationConnected message.
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
 *   <string>_rpc_applicationConnected:</string>
 *   <key>__argument</key>
 *   <dict>
 *     <key>WIRApplicationBundleIdentifierKey</key>
 *     <string>com.apple.mobilesafari</string>
 *     <key>WIRApplicationIdentifierKey</key>
 *     <string>PID:176</string>
 *     <key>WIRApplicationNameKey</key>
 *     <string>Safari</string>
 *     <key>WIRHostApplicationIdentifierKey</key>
 *     <string>PID:457</string>
 *     <key>WIRIsApplicationActiveKey</key>
 *     <integer>1</integer>
 *     <key>WIRIsApplicationProxyKey</key>
 *     <false/>
 *   </dict>
 * </dict>
 * </plist>
 * }</pre>
 */
@AutoValue
public abstract class ApplicationConnectedMessage extends ApplicationMessage {
  /** Returns a new builder. */
  public static Builder builder() {
    return new AutoValue_ApplicationConnectedMessage.Builder();
  }

  /** A builder for creating applicationConnected messages. */
  @AutoValue.Builder
  public abstract static class Builder extends ApplicationMessage.Builder<Builder> {
    @Override
    public abstract ApplicationConnectedMessage build();
  }

  @Override
  public MessageSelector selector() {
    return MessageSelector.APPLICATION_CONNECTED;
  }
}
