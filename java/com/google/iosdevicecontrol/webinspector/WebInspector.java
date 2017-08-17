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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.VisibleForTesting;
import java.io.Closeable;
import java.io.IOException;
import java.util.Optional;

/** A web inspector. */
public final class WebInspector implements Closeable {
  /** Connects to a web inspector running on a real device. */
  public static WebInspector connectToRealDevice(String udid) throws IOException {
    return connect(BinaryPlistSocket.openToRealDevice(udid));
  }

  /** Connects to a web inspector running on a simulator. */
  public static WebInspector connectToSimulator() throws IOException {
    return connect(BinaryPlistSocket.openToSimulator());
  }

  /**
   * Connect to the application with the specified application bundle identifier, using the
   * specified socket factory and notifying the specified listener of devtools messages.
   */
  private static WebInspector connect(InspectorSocket socket) throws IOException {
    return new WebInspector(socket);
  }

  private final InspectorSocket socket;

  @VisibleForTesting
  public WebInspector(InspectorSocket socket) {
    this.socket = checkNotNull(socket);
  }

  /** Sends a message to the web inspector. */
  public void sendMessage(InspectorMessage message) throws IOException {
    socket.sendMessage(message.toPlist());
  }

  /** Receives a message from the inspector socket or empty if the device socket is closed. */
  public Optional<InspectorMessage> receiveMessage() throws IOException {
    return socket.receiveMessage().map(InspectorMessage::fromPlist);
  }

  @Override
  public void close() throws IOException {
    socket.close();
  }
}
