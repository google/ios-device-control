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
import static com.google.common.truth.Truth.assertWithMessage;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class MessageKeyTest {
  @Test
  public void testToString() {
    assertThat(MessageKey.APPLICATION_IDENTIFIER.toString())
        .isEqualTo("WIRApplicationIdentifierKey");
  }

  @Test
  public void testUrlToString() {
    // The one exception to the otherwise nice toString conversion rule.
    assertThat(MessageKey.URL.toString()).isEqualTo("WIRURLKey");
  }

  @Test
  public void testForStringIsInverseOfToString() {
    for (MessageKey key : MessageKey.values()) {
      assertThat(MessageKey.forString(key.toString())).isEqualTo(key);
    }
  }

  @Test
  public void testForStringOfUnknownThrowsRuntimeException() {
    try {
      MessageKey.forString("gobbledygook");
      assertWithMessage("RuntimeException expected").fail();
    } catch (RuntimeException expected) {
    }
  }
}
