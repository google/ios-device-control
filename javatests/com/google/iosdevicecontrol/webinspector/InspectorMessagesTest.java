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

import static com.google.common.truth.Truth.assertWithMessage;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.dd.plist.NSDictionary;
import com.google.common.collect.ImmutableList;
import com.google.common.io.BaseEncoding;
import com.google.iosdevicecontrol.util.JsonParser;
import com.google.iosdevicecontrol.util.PlistParser;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class InspectorMessagesTest {
  private static final String PLIST_XML_FORMAT =
      "<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" "
          + "    \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">"
          + "<plist version=\"1.0\">"
          + "<dict>"
          + "  <key>__selector</key>"
          + "  <string>%s</string>"
          + "  <key>__argument</key>"
          + "  <dict>"
          + "  %s"
          + "  </dict>"
          + "</dict>"
          + "</plist>";

  @Test
  public void testApplicationConnectedMessage() {
    InspectorMessage message =
        ApplicationConnectedMessage.builder()
            .applicationBundleId("com.apple.mobilesafari")
            .applicationId("PID:176")
            .applicationName("Safari")
            .hostApplicationId("PID:457")
            .isApplicationActive(true)
            .isApplicationProxy(false)
            .build();
    String argumentsXml =
        "<key>WIRApplicationBundleIdentifierKey</key>"
            + "<string>com.apple.mobilesafari</string>"
            + "<key>WIRApplicationIdentifierKey</key>"
            + "<string>PID:176</string>"
            + "<key>WIRApplicationNameKey</key>"
            + "<string>Safari</string>"
            + "<key>WIRHostApplicationIdentifierKey</key>"
            + "<string>PID:457</string>"
            + "<key>WIRIsApplicationActiveKey</key>"
            + "<integer>1</integer>"
            + "<key>WIRIsApplicationProxyKey</key>"
            + "<false/>";
    testPlistConversion(message, argumentsXml);
  }

  @Test
  public void testApplicationDisonnectedMessage() {
    InspectorMessage message =
        ApplicationDisconnectedMessage.builder()
            .applicationBundleId("com.apple.mobilesafari")
            .applicationId("PID:176")
            .applicationName("Safari")
            .hostApplicationId("PID:457")
            .isApplicationActive(true)
            .isApplicationProxy(false)
            .build();
    String argumentsXml =
        "<key>WIRApplicationBundleIdentifierKey</key>"
            + "<string>com.apple.mobilesafari</string>"
            + "<key>WIRApplicationIdentifierKey</key>"
            + "<string>PID:176</string>"
            + "<key>WIRApplicationNameKey</key>"
            + "<string>Safari</string>"
            + "<key>WIRHostApplicationIdentifierKey</key>"
            + "<string>PID:457</string>"
            + "<key>WIRIsApplicationActiveKey</key>"
            + "<integer>1</integer>"
            + "<key>WIRIsApplicationProxyKey</key>"
            + "<false/>";
    testPlistConversion(message, argumentsXml);
  }

  @Test
  public void testApplicationSentDataMessage() {
    String messageData = "{\"id\": 1, \"result\": true}";
    String messageDataBase64 = BaseEncoding.base64().encode(messageData.getBytes(UTF_8));
    InspectorMessage message =
        ApplicationSentDataMessage.builder()
            .applicationId("PID:176")
            .destination("C1EAD225-D6BC-44B9-9089-2D7CC2D2204C")
            .messageData(JsonParser.parseObject(messageData))
            .build();
    String argumentsXml =
        "<key>WIRApplicationIdentifierKey</key>"
            + "<string>PID:176</string>"
            + "<key>WIRDestinationKey</key>"
            + "<string>C1EAD225-D6BC-44B9-9089-2D7CC2D2204C</string>"
            + "<key>WIRMessageDataKey</key>"
            + "<data>"
            + messageDataBase64
            + "</data>";

    testPlistConversion(message, argumentsXml);
  }

  @Test
  public void testApplicationSentListingMessage() {
    InspectorMessage message =
        ApplicationSentListingMessage.builder()
            .applicationId("PID:176")
            .listing(
                // Importantly, one with a connection id and one without.
                ImmutableList.of(
                    InspectorPage.builder()
                        .pageId(1)
                        .title("Google")
                        .type("WIRTypeWeb")
                        .url("http://www.google.com")
                        .build(),
                    InspectorPage.builder()
                        .connectionId("17858421-36EF-4752-89F7-7A13ED5782C5")
                        .pageId(2)
                        .title("Yahoo")
                        .type("WIRTypeWeb")
                        .url("http://www.yahoo.com")
                        .build()))
            .build();
    String argumentsXml =
        "<key>WIRApplicationIdentifierKey</key>"
            + "<string>PID:176</string>"
            + "<key>WIRListingKey</key>"
            + "<dict>"
            + "  <key>1</key>"
            + "  <dict>"
            + "    <key>WIRPageIdentifierKey</key>"
            + "    <integer>1</integer>"
            + "    <key>WIRTitleKey</key>"
            + "    <string>Google</string>"
            + "    <key>WIRTypeKey</key>"
            + "    <string>WIRTypeWeb</string>"
            + "    <key>WIRURLKey</key>"
            + "    <string>http://www.google.com</string>"
            + "  </dict>"
            + "  <key>2</key>"
            + "  <dict>"
            + "    <key>WIRConnectionIdentifierKey</key>"
            + "    <string>17858421-36EF-4752-89F7-7A13ED5782C5</string>"
            + "    <key>WIRPageIdentifierKey</key>"
            + "    <integer>2</integer>"
            + "    <key>WIRTitleKey</key>"
            + "    <string>Yahoo</string>"
            + "    <key>WIRTypeKey</key>"
            + "    <string>WIRTypeWeb</string>"
            + "    <key>WIRURLKey</key>"
            + "    <string>http://www.yahoo.com</string>"
            + "  </dict>"
            + "</dict>";
    testPlistConversion(message, argumentsXml);
  }

  @Test
  public void testApplicationUpdatedMessage() {
    InspectorMessage message =
        ApplicationUpdatedMessage.builder()
            .applicationBundleId("com.apple.mobilesafari")
            .applicationId("PID:176")
            .applicationName("Safari")
            .hostApplicationId("PID:457")
            .isApplicationActive(true)
            .isApplicationProxy(false)
            .build();
    String argumentsXml =
        "<key>WIRApplicationBundleIdentifierKey</key>"
            + "<string>com.apple.mobilesafari</string>"
            + "<key>WIRApplicationIdentifierKey</key>"
            + "<string>PID:176</string>"
            + "<key>WIRApplicationNameKey</key>"
            + "<string>Safari</string>"
            + "<key>WIRHostApplicationIdentifierKey</key>"
            + "<string>PID:457</string>"
            + "<key>WIRIsApplicationActiveKey</key>"
            + "<integer>1</integer>"
            + "<key>WIRIsApplicationProxyKey</key>"
            + "<false/>";
    testPlistConversion(message, argumentsXml);
  }

  @Test
  public void testForwardGetListingMessage() {
    InspectorMessage message =
        ForwardGetListingMessage.builder()
            .applicationId("PID:176")
            .connectionId("17858421-36EF-4752-89F7-7A13ED5782C5")
            .build();
    String argumentsXml =
        "<key>WIRApplicationIdentifierKey</key>"
            + "<string>PID:176</string>"
            + "<key>WIRConnectionIdentifierKey</key>"
            + "<string>17858421-36EF-4752-89F7-7A13ED5782C5</string>";
    testPlistConversion(message, argumentsXml);
  }

  @Test
  public void testForwardSocketDataMessage() {
    String socketData = "{\"id\": 1, \"method\": \"Inspector.enable\"}";
    String socketDataBase64 = BaseEncoding.base64().encode(socketData.getBytes(UTF_8));
    InspectorMessage message =
        ForwardSocketDataMessage.builder()
            .applicationId("PID:176")
            .connectionId("17858421-36EF-4752-89F7-7A13ED5782C5")
            .pageId(1)
            .sender("945f1146-2aa3-4875-a4c2-21cace3c4ade")
            .socketData(JsonParser.parseObject(socketData))
            .build();
    String argumentsXml =
        "<key>WIRApplicationIdentifierKey</key>"
            + "<string>PID:176</string>"
            + "<key>WIRConnectionIdentifierKey</key>"
            + "<string>17858421-36EF-4752-89F7-7A13ED5782C5</string>"
            + "<key>WIRPageIdentifierKey</key>"
            + "<integer>1</integer>"
            + "<key>WIRSenderKey</key>"
            + "<string>945f1146-2aa3-4875-a4c2-21cace3c4ade</string>"
            + "<key>WIRSocketDataKey</key>"
            + "<data>"
            + socketDataBase64
            + "</data>";
    testPlistConversion(message, argumentsXml);
  }

  @Test
  public void testForwardSocketSetupMessage() {
    InspectorMessage message =
        ForwardSocketSetupMessage.builder()
            .applicationId("PID:176")
            .automaticallyPause(false)
            .connectionId("17858421-36EF-4752-89F7-7A13ED5782C5")
            .pageId(1)
            .sender("945f1146-2aa3-4875-a4c2-21cace3c4ade")
            .build();
    String argumentsXml =
        "<key>WIRApplicationIdentifierKey</key>"
            + "<string>PID:176</string>"
            + "<key>WIRAutomaticallyPause</key>"
            + "<false/>"
            + "<key>WIRConnectionIdentifierKey</key>"
            + "<string>17858421-36EF-4752-89F7-7A13ED5782C5</string>"
            + "<key>WIRPageIdentifierKey</key>"
            + "<integer>1</integer>"
            + "<key>WIRSenderKey</key>"
            + "<string>945f1146-2aa3-4875-a4c2-21cace3c4ade</string>";
    testPlistConversion(message, argumentsXml);
  }

  @Test
  public void testReportConnectedApplicationListMessage() {
    InspectorMessage message =
        ReportConnectedApplicationListMessage.builder()
            .applicationDictionary(
                ImmutableList.of(
                    // Importantly, one with a hostApplicationId, one without.
                    InspectorApplication.builder()
                        .applicationBundleId("com.apple.mobilesafari")
                        .applicationId("PID:176")
                        .applicationName("Safari")
                        .isApplicationActive(true)
                        .isApplicationProxy(false)
                        .build(),
                    InspectorApplication.builder()
                        .applicationBundleId("com.apple.WebKit.WebContent")
                        .applicationId("PID:263")
                        .applicationName("WebContent")
                        .hostApplicationId("PID:176")
                        .isApplicationActive(false)
                        .isApplicationProxy(true)
                        .build()))
            .build();
    String argumentsXml =
        "<key>WIRApplicationDictionaryKey</key>"
            + "<dict>"
            + "  <key>PID:176</key>"
            + "  <dict>"
            + "    <key>WIRApplicationBundleIdentifierKey</key>"
            + "    <string>com.apple.mobilesafari</string>"
            + "    <key>WIRApplicationIdentifierKey</key>"
            + "    <string>PID:176</string>"
            + "    <key>WIRApplicationNameKey</key>"
            + "    <string>Safari</string>"
            + "    <key>WIRIsApplicationActiveKey</key>"
            + "    <integer>1</integer>"
            + "    <key>WIRIsApplicationProxyKey</key>"
            + "    <false/>"
            + "  </dict>"
            + "  <key>PID:263</key>"
            + "  <dict>"
            + "    <key>WIRApplicationBundleIdentifierKey</key>"
            + "    <string>com.apple.WebKit.WebContent</string>"
            + "    <key>WIRApplicationIdentifierKey</key>"
            + "    <string>PID:263</string>"
            + "    <key>WIRApplicationNameKey</key>"
            + "    <string>WebContent</string>"
            + "    <key>WIRHostApplicationIdentifierKey</key>"
            + "    <string>PID:176</string>"
            + "    <key>WIRIsApplicationActiveKey</key>"
            + "    <integer>0</integer>"
            + "    <key>WIRIsApplicationProxyKey</key>"
            + "    <true/>"
            + "  </dict>"
            + "</dict>";
    testPlistConversion(message, argumentsXml);
  }

  @Test
  public void testReportConnectedDriverListMessageMessage() {
    // TODO(user): Add drivers here when we actually know what they look like.
    InspectorMessage message =
        ReportConnectedDriverListMessage.builder().driverDictionary(ImmutableList.of()).build();
    String argumentsXml = "<key>WIRDriverDictionaryKey</key><dict></dict>";
    testPlistConversion(message, argumentsXml);
  }

  @Test
  public void testReportIdentifierMessage() {
    InspectorMessage message =
        ReportIdentifierMessage.builder()
            .connectionId("17858421-36EF-4752-89F7-7A13ED5782C5")
            .build();
    String argumentsXml =
        "<key>WIRConnectionIdentifierKey</key>"
            + "<string>17858421-36EF-4752-89F7-7A13ED5782C5</string>";
    testPlistConversion(message, argumentsXml);
  }

  @Test
  public void testReportSetupMessageForRealDevice() {
    InspectorMessage message = ReportSetupMessage.builder().build();
    String argumentsXml = "";
    testPlistConversion(message, argumentsXml);
  }

  @Test
  public void testReportSetupMessageForSimulator() {
    InspectorMessage message =
        ReportSetupMessage.builder()
            .simulatorBuild("11D167")
            .simulatorName("iPhone Simulator")
            .simulatorProductVersion("7.1")
            .build();
    String argumentsXml =
        "<key>WIRSimulatorBuildKey</key>"
            + "<string>11D167</string>"
            + "<key>WIRSimulatorNameKey</key>"
            + "<string>iPhone Simulator</string>"
            + "<key>WIRSimulatorProductVersionKey</key>"
            + "<string>7.1</string>";
    testPlistConversion(message, argumentsXml);
  }

  private static void testPlistConversion(InspectorMessage message, String argumentsXml) {
    // Test that parsing the given plist XML yields yields the given message.
    String plistXml = String.format(PLIST_XML_FORMAT, message.selector(), argumentsXml);
    assertPlistParsesTo(message, plistXml);

    // Test that parsing the result of toPlistXml yields the message back.
    assertPlistParsesTo(message, message.toPlist().toXMLPropertyList());
  }

  private static void assertPlistParsesTo(InspectorMessage message, String plistXml) {
    InspectorMessage parsed =
        InspectorMessage.fromPlist((NSDictionary) PlistParser.fromXml(plistXml));
    assertWithMessage(plistXml).that(parsed).isEqualTo(message);
  }
}
