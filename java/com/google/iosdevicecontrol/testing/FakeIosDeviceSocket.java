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

package com.google.iosdevicecontrol.testing;

import com.google.common.io.ByteStreams;
import com.google.iosdevicecontrol.IosDevice;
import com.google.iosdevicecontrol.IosDeviceException;
import com.google.iosdevicecontrol.IosDeviceSocket;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** Fake implementaton of IosDeviceSocket. s */
public class FakeIosDeviceSocket extends IosDeviceSocket {
  private volatile boolean closed;
  private final ByteArrayInputStream input;
  private final ByteArrayOutputStream output;

  public FakeIosDeviceSocket(IosDevice device, byte[] inputBytes) {
    super(device);
    input = new ByteArrayInputStream(inputBytes);
    output = new ByteArrayOutputStream();
  }

  @Override
  public int read(byte[] bytes) throws IosDeviceException {
    if (closed) {
      throw new IosDeviceException(device(), "socket closed");
    }
    try {
      return ByteStreams.read(input, bytes, 0, bytes.length);
    } catch (IOException e) {
      throw new IosDeviceException(device(), e);
    }
  }

  @Override
  public void write(byte[] bytes) throws IosDeviceException {
    if (closed) {
      throw new IosDeviceException(device(), "socket closed");
    }
    try {
      output.write(bytes);
    } catch (IOException e) {
      throw new IosDeviceException(device(), e);
    }
  }

  @Override
  public void close() throws IosDeviceException {
    closed = true;
  }

  /** Return an array of all bytes written to the socket. */
  public byte[] bytesWritten() {
    return output.toByteArray();
  }
}
