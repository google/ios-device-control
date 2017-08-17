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

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_UNDERSCORE;

import com.google.iosdevicecontrol.util.StringEnumMap;
import java.util.function.Supplier;

/** The type of a web inspector protocol message. */
public enum MessageSelector {
  APPLICATION_CONNECTED(ApplicationConnectedMessage::builder),
  APPLICATION_DISCONNECTED(ApplicationDisconnectedMessage::builder),
  APPLICATION_SENT_DATA(ApplicationSentDataMessage::builder),
  APPLICATION_SENT_LISTING(ApplicationSentListingMessage::builder),
  APPLICATION_UPDATED(ApplicationUpdatedMessage::builder),
  FORWARD_GET_LISTING(ForwardGetListingMessage::builder),
  FORWARD_SOCKET_DATA(ForwardSocketDataMessage::builder),
  FORWARD_SOCKET_SETUP(ForwardSocketSetupMessage::builder),
  REPORT_CONNECTED_APPLICATION_LIST(ReportConnectedApplicationListMessage::builder),
  REPORT_CONNECTED_DRIVER_LIST(ReportConnectedDriverListMessage::builder),
  REPORT_IDENTIFIER(ReportIdentifierMessage::builder),
  REPORT_SETUP(ReportSetupMessage::builder);

  private static final StringEnumMap<MessageSelector> STRING_TO_SELECTOR =
      new StringEnumMap<>(MessageSelector.class);

  /**
   * Returns the <code>MessageSelector</code> for the specified string, e.g. returns <code>
   * REPORT_SETUP</code> for the string "_rpc_reportSetup".
   */
  public static MessageSelector forString(String s) {
    return STRING_TO_SELECTOR.get(s);
  }

  private final String string;

  @SuppressWarnings("ImmutableEnumChecker")
  private final Supplier<InspectorMessage.Builder> newMessageBuilder;

  private MessageSelector(Supplier<InspectorMessage.Builder> newMessageBuilder) {
    // E.g. "REPORT_SETUP" becomes "_rpc_reportSetup:"
    string = "_rpc_" + UPPER_UNDERSCORE.to(LOWER_CAMEL, name()) + ":";
    this.newMessageBuilder = newMessageBuilder;
  }

  InspectorMessage.Builder newMessageBuilder() {
    return newMessageBuilder.get();
  }

  @Override
  public String toString() {
    return string;
  }
}
