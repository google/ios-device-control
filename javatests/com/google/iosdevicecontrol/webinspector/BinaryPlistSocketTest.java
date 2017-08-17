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

import com.dd.plist.BinaryPropertyListWriter;
import com.dd.plist.NSDictionary;
import com.dd.plist.NSObject;
import com.google.common.primitives.Ints;
import com.google.iosdevicecontrol.util.ForwardingSocket;
import com.google.iosdevicecontrol.util.PlistParser;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class BinaryPlistSocketTest {
  @Test
  public void testSendMessage() throws IOException {
    NSDictionary inputMessage = new NSDictionary();
    inputMessage.put("hello", "world");

    FakeSocket fakeSocket = new FakeSocket(new byte[] {});
    try (InspectorSocket socket = new BinaryPlistSocket(fakeSocket)) {
      socket.sendMessage(inputMessage);
    }

    byte[] outputBytes = fakeSocket.outputBytes();
    byte[] messageBytes = Arrays.copyOfRange(outputBytes, 4, outputBytes.length);
    NSObject outputMessage = PlistParser.fromBinary(messageBytes);
    assertThat(outputMessage).isEqualTo(inputMessage);
  }

  @Test
  public void testReceiveMessage() throws IOException {
    NSDictionary inputMessage = new NSDictionary();
    inputMessage.put("goodbye", "world");
    byte[] messageBytes = BinaryPropertyListWriter.writeToArray(inputMessage);
    byte[] lengthBytes = Ints.toByteArray(messageBytes.length);
    byte[] inputBytes = new byte[messageBytes.length + 4];
    System.arraycopy(lengthBytes, 0, inputBytes, 0, 4);
    System.arraycopy(messageBytes, 0, inputBytes, 4, messageBytes.length);

    FakeSocket fakeSocket = new FakeSocket(inputBytes);
    try (InspectorSocket socket = new BinaryPlistSocket(fakeSocket)) {
      assertThat(socket.receiveMessage().get()).isEqualTo(inputMessage);
    }
  }

  @Test
  public void testReceiveEOF() throws IOException {
    NSDictionary inputMessage = new NSDictionary();
    inputMessage.put("goodbye", "world");
    byte[] messageBytes = BinaryPropertyListWriter.writeToArray(inputMessage);
    byte[] lengthBytes = Ints.toByteArray(messageBytes.length + 1);
    byte[] inputBytes = new byte[messageBytes.length + 4];
    System.arraycopy(lengthBytes, 0, inputBytes, 0, 4);
    System.arraycopy(messageBytes, 0, inputBytes, 4, messageBytes.length);

    FakeSocket fakeSocket = new FakeSocket(inputBytes);
    try (InspectorSocket socket = new BinaryPlistSocket(fakeSocket)) {
      assertThat(socket.receiveMessage().isPresent()).isFalse();
    }
  }

  private static final class FakeSocket extends ForwardingSocket {
    private final InputStream socketIn;
    private final ByteArrayOutputStream socketOut = new ByteArrayOutputStream();

    private FakeSocket(byte[] inputBytes) {
      super(new Socket());
      socketIn = new ByteArrayInputStream(inputBytes);
    }

    private byte[] outputBytes() {
      return socketOut.toByteArray();
    }

    @Override
    public InputStream getInputStream() {
      return socketIn;
    }

    @Override
    public OutputStream getOutputStream() {
      return socketOut;
    }
  }
}
