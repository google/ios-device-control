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

import static com.google.iosdevicecontrol.command.Command.command;

import com.dd.plist.BinaryPropertyListWriter;
import com.dd.plist.NSDictionary;
import com.google.iosdevicecontrol.util.FluentLogger;
import com.google.common.io.ByteStreams;
import com.google.iosdevicecontrol.command.CommandFailureException;
import com.google.iosdevicecontrol.command.CommandProcess;
import com.google.iosdevicecontrol.command.CommandResult;
import com.google.iosdevicecontrol.command.CommandStartException;
import com.google.common.primitives.Ints;
import com.google.iosdevicecontrol.util.ForwardingSocket;
import com.google.iosdevicecontrol.util.PlistParser;
import com.google.iosdevicecontrol.util.PlistParser.PlistParseException;
import com.google.iosdevicecontrol.util.RetryCallable;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;

import java.net.Socket;
import java.util.Optional;
import org.joda.time.Duration;

/** A web inspector socket that sends and receives plists in binary format. */
final class BinaryPlistSocket implements InspectorSocket {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Open a web inspector socket to a real device with the specified udid. */
  static InspectorSocket openToRealDevice(String udid) throws IOException {
    int inspectorPort;
    try (ServerSocket socket = new ServerSocket(0)) {
      inspectorPort = socket.getLocalPort();
    }
    CommandProcess inspectorProcess;
    try {
      inspectorProcess =
          command(
                  "/usr/local/bin/idevicewebinspectorproxy",
                  "-u",
                  udid,
                  Integer.toString(inspectorPort))
              .start();
    } catch (CommandStartException e) {
      throw new IOException(e);
    }

    Closeable closeProcess =
        () -> {
          try {
            CommandResult result = inspectorProcess.kill().await();
            logger.atInfo().log("Web inspector proxy result: %s", result);
          } catch (CommandFailureException e) {
            throw new IOException(e);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
          }
        };

    // Socket may not be open right away, so we retry.
    Socket socket;
    try {
      socket =
          RetryCallable.<Socket, IOException>retry(() -> new Socket("localhost", inspectorPort))
              .withDelay(Duration.standardSeconds(1))
              .withMaxAttempts(15)
              .call();
    } catch (Exception e) {
      closeProcess.close();
      throw e;
    }

    // Wrap the socket with an augmented close method that also closes the process.
    return new BinaryPlistSocket(
        new ForwardingSocket(socket) {
          @Override
          public synchronized void close() throws IOException {
            try {
              closeProcess.close();
            } finally {
              super.close();
            }
          }
        });
  }

  /** Open a web inspector socket to a simulator. */
  static InspectorSocket openToSimulator() throws IOException {
    return new BinaryPlistSocket(new Socket("::1", 27753)); // IPv6 loopback address required
  }

  private final InputStream socketIn;
  private final OutputStream socketOut;
  private final Closeable socketClose;

  BinaryPlistSocket(Socket socket) throws IOException {
    try {
      socketIn = socket.getInputStream();
      socketOut = socket.getOutputStream();
    } catch (IOException | RuntimeException e) {
      try {
        socket.close();
      } catch (IOException ce) {
        e.addSuppressed(ce);
      }
      throw e;
    }
    socketClose = socket::close;
  }

  @Override
  public void sendMessage(NSDictionary message) throws IOException {
    byte[] messageBytes = BinaryPropertyListWriter.writeToArray(message);
    byte[] lengthBytes = Ints.toByteArray(messageBytes.length);
    socketOut.write(lengthBytes);
    socketOut.write(messageBytes);
  }

  @Override
  public Optional<NSDictionary> receiveMessage() throws IOException {
    try {
      byte[] lengthBytes = new byte[4];
      ByteStreams.readFully(socketIn, lengthBytes);
      byte[] messageBytes = new byte[Ints.fromByteArray(lengthBytes)];
      ByteStreams.readFully(socketIn, messageBytes);
      return Optional.of((NSDictionary) PlistParser.fromBinary(messageBytes));
    } catch (EOFException e) {
      return Optional.empty();
    } catch (PlistParseException e) {
      throw new IOException(e);
    }
  }

  @Override
  public void close() throws IOException {
    socketClose.close();
  }
}
