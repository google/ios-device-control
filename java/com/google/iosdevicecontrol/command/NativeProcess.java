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

package com.google.iosdevicecontrol.command;

import com.google.common.base.Optional;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ProcessBuilder.Redirect;
import java.util.concurrent.TimeUnit;

/** An implementation of {@link CommandProcess} that runs natively on the OS. */
final class NativeProcess extends CommandProcess {
  /** A {@link CommandExecutor} that runs commands as native processes. */
  static final CommandExecutor EXECUTOR =
      new CommandExecutor() {
        @Override
        public CommandProcess start(Command command) throws CommandStartException {
          ProcessBuilder processBuilder = new ProcessBuilder();
          processBuilder.command().add(command.executable());
          processBuilder.command().addAll(command.arguments());
          processBuilder.environment().clear();
          processBuilder.environment().putAll(command.environment());
          if (command.workingDirectory().isPresent()) {
            processBuilder.directory(command.workingDirectory().get().toFile());
          }

          // Special cases for stdin redirection with a native process.
          if (command.stdinSource().kind().equals(InputSource.Kind.FILE)) {
            try {
              processBuilder.redirectInput(command.stdinSource().file().toFile());
            } catch (UnsupportedOperationException e) {
              // Treat Paths not on the default filesystem as streams.
            }
          } else if (command.stdinSource().kind().equals(InputSource.Kind.JVM)) {
            processBuilder.redirectInput(Redirect.INHERIT);
          }

          // Special cases for stdout redirection with a native process.
          // TODO(user): Add cases for FILE_APPEND, JVM_OUT, and JVM_ERR.
          if (command.stdoutSink().kind().equals(OutputSink.Kind.FILE)) {
            try {
              processBuilder.redirectOutput(command.stdoutSink().file().toFile());
            } catch (UnsupportedOperationException e) {
              // Treat Paths not on the default filesystem as streams.
            }
          }

          // Special cases for stderr redirection with a native process.
          // TODO(user): Add cases for FILE_APPEND, JVM_OUT, and JVM_ERR.
          if (command.stderrSink().kind().equals(OutputSink.Kind.FILE)) {
            try {
              processBuilder.redirectError(command.stderrSink().file().toFile());
            } catch (UnsupportedOperationException e) {
              // Treat Paths not on the default filesystem as streams.
            }
          }

          try {
            return new NativeProcess(command, new NativeRawProcess(processBuilder));
          } catch (IOException e) {
            throw new CommandStartException(command, e);
          }
        }
      };

  private NativeProcess(Command command, NativeRawProcess rawProcess) throws CommandStartException {
    super(command, rawProcess);
  }

  private static final class NativeRawProcess implements RawProcess {
    private final Process process;
    private final Optional<OutputStream> stdinPipe;
    private final Optional<InputStream> stdoutPipe;
    private final Optional<InputStream> stderrPipe;

    private NativeRawProcess(ProcessBuilder processBuilder) throws IOException {
      process = processBuilder.start();
      stdinPipe = maybePipe(processBuilder.redirectInput(), process.getOutputStream());
      stdoutPipe = maybePipe(processBuilder.redirectOutput(), process.getInputStream());
      stderrPipe = maybePipe(processBuilder.redirectError(), process.getErrorStream());
    }

    private static <T> Optional<T> maybePipe(Redirect redirect, T stream) {
      return redirect.type() == Redirect.Type.PIPE ? Optional.of(stream) : Optional.<T>absent();
    }

    @Override
    public Optional<OutputStream> stdinPipe() {
      return stdinPipe;
    }

    @Override
    public Optional<InputStream> stdoutPipe() {
      return stdoutPipe;
    }

    @Override
    public Optional<InputStream> stderrPipe() {
      return stderrPipe;
    }

    @Override
    public boolean isAlive() {
      // TODO(user): Replace with Process.isAlive() in Java 8.
      try {
        process.exitValue();
        return false;
      } catch (IllegalThreadStateException e) {
        return true;
      }
    }

    @Override
    public int await() throws InterruptedException {
      return process.waitFor();
    }

    @Override
    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
      // TODO(user) Replace with process.waitFor(timeout, unit) in Java 8.
      long currentTime = System.nanoTime();
      long stopTime = currentTime + unit.toNanos(timeout);

      while (isAlive()) {
        if (currentTime >= stopTime) {
          return false;
        }
        Thread.sleep(Math.min(TimeUnit.NANOSECONDS.toMillis(stopTime - currentTime), 100));
        currentTime = System.nanoTime();
      }
      return true;
    }

    @Override
    public void kill() {
      process.destroy();
    }
  }
}
