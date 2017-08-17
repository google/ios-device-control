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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

import com.google.iosdevicecontrol.testing.FakeInspectorSocket;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Created by gdennis on 6/6/17. */
@RunWith(JUnit4.class)
public class WebInspectorTest {
  private static final InspectorMessage MESSAGE1 =
      ReportIdentifierMessage.builder().connectionId("id1").build();
  private static final InspectorMessage MESSAGE2 =
      ApplicationConnectedMessage.builder()
          .applicationBundleId("com.apple.mobile.safari")
          .applicationId("123")
          .applicationName("Safari")
          .isApplicationActive(true)
          .isApplicationProxy(false)
          .build();

  private FakeInspectorSocket fakeInspectorSocket;
  private WebInspector inspector;

  @Before
  public void setup() {
    fakeInspectorSocket = new FakeInspectorSocket();
    inspector = new WebInspector(fakeInspectorSocket);
  }

  @Test
  public void testSendMessage() throws IOException {
    inspector.sendMessage(MESSAGE1);
    inspector.sendMessage(MESSAGE2);
    assertThat(fakeInspectorSocket.dequeueMessagesSent()).containsExactly(MESSAGE1, MESSAGE2);
  }

  @Test
  public void testReceiveMessage() throws IOException {
    fakeInspectorSocket.enqueueMessageToReceive(MESSAGE1);
    fakeInspectorSocket.enqueueMessageToReceive(MESSAGE2);
    assertThat(inspector.receiveMessage()).hasValue(MESSAGE1);
    assertThat(inspector.receiveMessage()).hasValue(MESSAGE2);
  }

  @Test
  public void testClose() throws IOException {
    assertThat(fakeInspectorSocket.isClosed()).isFalse();
    inspector.close();
    assertThat(fakeInspectorSocket.isClosed()).isTrue();
  }
}
