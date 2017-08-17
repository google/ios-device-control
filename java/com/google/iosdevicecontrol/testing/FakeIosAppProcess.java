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

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import com.google.iosdevicecontrol.IosAppProcess;
import com.google.iosdevicecontrol.IosDevice;
import com.google.iosdevicecontrol.IosDeviceException;
import com.google.iosdevicecontrol.util.CheckedCallable;
import com.google.iosdevicecontrol.util.CheckedCallables;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/** Fake implementation of {@link IosAppProcess}. */
public class FakeIosAppProcess implements IosAppProcess {
  private final IosDevice device;
  private final SettableFuture<String> future;

  public FakeIosAppProcess(IosDevice device) {
    this.device = checkNotNull(device);
    future = SettableFuture.create();
  }

  @Override
  public FakeIosAppProcess kill() {
    future.cancel(false);
    return this;
  }

  @Override
  public String await() throws IosDeviceException {
    try {
      return future.get();
    } catch (ExecutionException e) {
      throw mapExecutionException(e);
    } catch (InterruptedException e) {
      throw mapInterruptedException(e);
    }
  }

  @Override
  public String await(Duration timeout) throws IosDeviceException, TimeoutException {
    try {
      return future.get(timeout.toNanos(), NANOSECONDS);
    } catch (ExecutionException e) {
      throw mapExecutionException(e);
    } catch (InterruptedException e) {
      throw mapInterruptedException(e);
    }
  }

  @Override
  public Reader outputReader() {
    CheckedCallable<Reader, IOException> delegate =
        CheckedCallables.memoize(
            () -> new StringReader(Futures.getChecked(future, IOException.class)));
    return new Reader() {
      @Override
      public int read(char[] cbuf, int off, int len) throws IOException {
        return delegate.call().read(cbuf, off, len);
      }

      @Override
      public void close() throws IOException {
        delegate.call().close();
      }
    };
  }

  public void setOutput(String s) {
    future.set(s);
  }

  public void setException(Throwable throwable) {
    future.setException(throwable);
  }

  private IosDeviceException mapExecutionException(Exception e) throws IosDeviceException {
    throw new IosDeviceException(device, e.getCause());
  }

  private IosDeviceException mapInterruptedException(InterruptedException e)
      throws IosDeviceException {
    Thread.currentThread().interrupt();
    throw new IosDeviceException(device, e);
  }
}
