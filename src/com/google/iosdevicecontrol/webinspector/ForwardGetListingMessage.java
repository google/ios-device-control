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
 * A forwardGetListing message.
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
 *   <string>_rpc_forwardGetListing:</string>
 *   <key>__argument</key>command
 *   <dict>
 *     <key>WIRApplicationIdentifierKey</key>
 *     <string>PID:176</string>
 *     <key>WIRConnectionIdentifierKey</key>
 *     <string>17858421-36EF-4752-89F7-7A13ED5782C5</string>
 *   </dict>
 * </dict>
 * </plist>
 * }</pre>
 */
@AutoValue
public abstract class ForwardGetListingMessage extends InspectorMessage {
  /** Returns a new builder. */
  public static Builder builder() {
    return new AutoValue_ForwardGetListingMessage.Builder();
  }

  /** A builder for creating forwardGetListing messages. */
  @AutoValue.Builder
  public abstract static class Builder extends InspectorMessage.Builder {
    @Override
    public abstract Builder applicationId(String applicationId);

    @Override
    public abstract Builder connectionId(String connectionId);

    @Override
    public abstract ForwardGetListingMessage build();
  }

  @Override
  public abstract String applicationId();

  @Override
  public abstract String connectionId();

  @Override
  public MessageSelector selector() {
    return MessageSelector.FORWARD_GET_LISTING;
  }
}
