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

package com.google.iosdevicecontrol.command.testing;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Optional;
import com.google.common.base.VerifyException;
import com.google.common.io.ByteStreams;
import com.google.iosdevicecontrol.command.Command;
import com.google.iosdevicecontrol.command.CommandProcess;
import com.google.iosdevicecontrol.command.CommandStartException;
import com.google.common.util.concurrent.SettableFuture;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** A fake implementation of {@link Process}. */
public final class FakeProcess extends CommandProcess {
  private final FakeRawProcess rawProcess;

  public static FakeProcess start(Command command) throws CommandStartException {
    return new FakeProcess(command, new FakeRawProcess());
  }

  private FakeProcess(Command command, FakeRawProcess rawProcess) throws CommandStartException {
    super(command, rawProcess);
    this.rawProcess = rawProcess;
  }

  /**
   * Sets the process as terminated with the specified exit code. This has no effect if the process
   * is already terminated.
   */
  public void setTerminated(int exitCode) {
    rawProcess.setTerminated(exitCode);
  }

  /** Returns whether this process was killed. */
  public boolean wasKilled() {
    return rawProcess.killed;
  }

  /**
   * Provides fake stdout bytes to the process.
   *
   * @throws IllegalStateException - if stdout is not writable (e.g. the process has terminated).
   */
  public void writeStdout(byte[] output) {
    writeToPipedOutputStream(rawProcess.stdoutOutputStream, output);
  }

  /**
   * Provides fake stdout bytes to the process as a UTF-8 encoded string.
   *
   * @throws IllegalStateException - if stdout is not writable (e.g. the process has terminated).
   */
  public void writeStdoutUtf8(String output) {
    writeStdout(output.getBytes(UTF_8));
  }

  /**
   * Provides fake stderr bytes to the process.
   *
   * @throws IllegalStateException - if stderr is not writable (e.g. the process has terminated).
   */
  public void writeStderr(byte[] error) {
    writeToPipedOutputStream(rawProcess.stderrOutputStream, error);
  }

  /**
   * Provides fake stderr bytes to the process as a UTF-8 encoded string.
   *
   * @throws IllegalStateException - if stderr is not writable (e.g. the process has terminated).
   */
  public void writeStderrUtf8(String error) {
    writeStderr(error.getBytes(UTF_8));
  }

  /**
   * Writes bytes to a PipedOutputStream and translates any IOException to an IllegalStateException.
   *
   * <p>PipedOutputStream throws IOException on write, not due to any environmental conditions, only
   * due to it being in an unwritable state. Some such states, such as not being connected to a
   * PipedInputStream, are impossible here. The rest, such as the fake process having terminated,
   * are rare and user-preventable, particuarly in the context of a test, which is expected to have
   * deterministic behavior. Moreover, if the write() methods did declare IOException, a test would
   * surely just rethrow it, so it's only impact would be added noise to the test code.
   */
  private void writeToPipedOutputStream(PipedOutputStream pipedOutput, byte[] bytes) {
    try {
      pipedOutput.write(bytes);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static final class FakeRawProcess implements RawProcess {
    private final SettableFuture<Integer> exitCodeFuture = SettableFuture.create();
    private final PipedInputStream stdout = new PipedInputStream();
    private final PipedInputStream stderr = new PipedInputStream();
    private final PipedOutputStream stdoutOutputStream;
    private final PipedOutputStream stderrOutputStream;

    private volatile boolean killed = false;

    private FakeRawProcess() {
      try {
        stdoutOutputStream = new PipedOutputStream(stdout);
        stderrOutputStream = new PipedOutputStream(stderr);
      } catch (IOException e) {
        // Can only happen if piped input streams are already connected, and we know they are not.
        throw new VerifyException(e);
      }
    }

    @Override
    public Optional<OutputStream> stdinPipe() {
      return Optional.of(ByteStreams.nullOutputStream());
    }

    @Override
    public Optional<InputStream> stdoutPipe() {
      return Optional.<InputStream>of(stdout);
    }

    @Override
    public Optional<InputStream> stderrPipe() {
      return Optional.<InputStream>of(stderr);
    }

    @Override
    public boolean isAlive() {
      return !exitCodeFuture.isDone();
    }

    @Override
    public int await() throws InterruptedException {
      try {
        return exitCodeFuture.get();
      } catch (ExecutionException e) {
        throw new VerifyException(e);
      }
    }

    @Override
    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
      try {
        exitCodeFuture.get(timeout, unit);
        return true;
      } catch (TimeoutException e) {
        return false;
      } catch (ExecutionException e) {
        throw new VerifyException(e);
      }
    }

    @Override
    public void kill() {
      setTerminated(143); // Common exit code for SIGTERM
      killed = true;
    }

    /**
     * Sets the process as terminated with the specified exit code. This has no effect if the
     * process is already terminated.
     */
    private void setTerminated(int exitCode) {
      exitCodeFuture.set(exitCode);
      try {
        stdoutOutputStream.close();
        stderrOutputStream.close();
      } catch (IOException e) {
        // PipedOutputStream#close never throws IOException despite declaring it.
        throw new VerifyException(e);
      }
    }
  }
}
