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

package com.google.iosdevicecontrol;

import com.google.common.io.ByteStreams;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/** A socket to communicate to an iOS device. */
public abstract class IosDeviceSocket extends IosDeviceResource {
  /** Wraps an iOS device and a Java socket as an IosDeviceSocket. */
  public static IosDeviceSocket wrap(IosDevice device, Socket socket) throws IosDeviceException {
    return new SocketWrapper(device, socket);
  }

  protected IosDeviceSocket(IosDevice device) {
    super(device);
  }

  /**
   * Read bytes from the socket until the array is full or EOF is reached, returning the number of
   * bytes read. The returned value is less than the length of the array only when EOF is reached.
   */
  public abstract int read(byte[] bytes) throws IosDeviceException;

  /** Writes bytes to the socket. */
  public abstract void write(byte[] bytes) throws IosDeviceException;

  /** Wraps a device and a Java socket as an IosDeviceSocket. */
  private static class SocketWrapper extends IosDeviceSocket {
    private final InputStream socketIn;
    private final OutputStream socketOut;
    private final Closeable socketClose;

    private SocketWrapper(IosDevice device, Socket socket) throws IosDeviceException {
      super(device);
      try {
        socketIn = socket.getInputStream();
        socketOut = socket.getOutputStream();
      } catch (IOException | RuntimeException e) {
        try {
          socket.close();
        } catch (IOException ce) {
          e.addSuppressed(ce);
        }
        throw new IosDeviceException(device, e);
      }
      socketClose = socket::close;
    }

    @Override
    public int read(byte[] bytes) throws IosDeviceException {
      try {
        return ByteStreams.read(socketIn, bytes, 0, bytes.length);
      } catch (IOException e) {
        throw new IosDeviceException(device(), e);
      }
    }

    @Override
    public void write(byte[] bytes) throws IosDeviceException {
      try {
        socketOut.write(bytes);
      } catch (IOException e) {
        throw new IosDeviceException(device(), e);
      }
    }

    @Override
    public void close() throws IosDeviceException {
      try {
        socketClose.close();
      } catch (IOException e) {
        throw new IosDeviceException(device(), e);
      }
    }
  }
}
