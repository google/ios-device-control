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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Verify.verify;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.MoreObjects;
import com.google.common.base.Optional;
import com.google.common.base.Supplier;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;

/**
 * A running process, generally started via {@link Command#start}. This class allows you to interact
 * with the process as it executes asynchronously. Use {@link #await} or {@link Command#execute} to
 * cause the current thread to block until the process terminates.
 *
 * <p><b>Note:</b> native processes write output to fixed-size buffers and will block after either
 * buffer is full. Therefore starting a subprocess without reading stdout and stderr risks causing
 * the subprocess to stall. Calling {@code .await()} will flush these buffers for you.
 */
public abstract class CommandProcess {
  private final Command command;
  private final RawProcess rawProcess;
  private final CapturingOutputStream stdoutStream;
  private final CapturingOutputStream stderrStream;
  private final Optional<AsyncCopier> stdinPump;
  private final Optional<AsyncCopier> stdoutPump;
  private final Optional<AsyncCopier> stderrPump;

  private volatile CommandResult result = null;

  protected CommandProcess(Command command, RawProcess rawProcess) throws CommandStartException {
    this.command = checkNotNull(command);
    this.rawProcess = checkNotNull(rawProcess);
    stdoutStream = new CapturingOutputStream();
    stderrStream = new CapturingOutputStream();

    Supplier<Level> ioLogLevel = new Supplier () {
      @Override
      public Level get() {
        return CommandProcess.this.rawProcess.isAlive() ? Level.WARNING : Level.FINE;
      }
    };

    try {
      stdinPump =
          maybeStartCopyFromSourceToPipe(command.stdinSource(), rawProcess.stdinPipe(), ioLogLevel);
      stdoutPump =
          maybeStartCopyFromPipeToSink(
              rawProcess.stdoutPipe(),
              command.stdoutSink(),
              stdoutStream,
              stderrStream,
              ioLogLevel);
      stderrPump =
          maybeStartCopyFromPipeToSink(
              rawProcess.stderrPipe(),
              command.stderrSink(),
              stdoutStream,
              stderrStream,
              ioLogLevel);
    } catch (IOException e) {
      throw new CommandStartException(command, e);
    }
  }

  private static Optional<AsyncCopier> maybeStartCopyFromSourceToPipe(
      InputSource inputSource, Optional<OutputStream> pipe, Supplier<Level> ioLogLevel)
      throws IOException {
    if (pipe.isPresent() && !inputSource.kind().equals(InputSource.Kind.PROCESS)) {
      InputStream stdinSource = inputSource.byteSource().get().openStream();
      return Optional.of(AsyncCopier.start(stdinSource, pipe.get(), ioLogLevel));
    } else {
      return Optional.absent();
    }
  }

  private static Optional<AsyncCopier> maybeStartCopyFromPipeToSink(
      Optional<InputStream> pipe,
      OutputSink outputSink,
      OutputStream stdoutStream,
      OutputStream stderrStream,
      Supplier<Level> ioLogLevel)
      throws IOException {
    if (pipe.isPresent()) {
      OutputStream sinkStream =
          outputSink.kind().equals(OutputSink.Kind.PROCESS_OUT)
              ? stdoutStream
              : outputSink.kind().equals(OutputSink.Kind.PROCESS_ERR)
                  ? stderrStream
                  : outputSink.byteSink().get().openStream();
      return Optional.of(AsyncCopier.start(pipe.get(), sinkStream, ioLogLevel));
    } else {
      return Optional.absent();
    }
  }

  /** The command that started this process. */
  public final Command command() {
    return command;
  }

  /** Returns whether the process has not yet terminated. */
  public final boolean isAlive() {
    return rawProcess.isAlive();
  }

  /**
   * Blocks until the command completes. It returns the command result if the result satisfies the
   * command's success condition (by default a zero exit code), and throws {@link
   * CommandFailureException} otherwise.
   *
   * <p>If this thread is interrupted, an {@link InterruptedException} is thrown, but the <i>process
   * will continue running</i>. If you wish the process to be killed in this case, catch this
   * exception and call {@link CommandProcess#kill} on the running process.
   *
   * @throws CommandFailureException - if the result fails the command's success condition
   * @throws InterruptedException - if the execution is interrupted
   */
  @CanIgnoreReturnValue
  public final CommandResult await() throws CommandFailureException, InterruptedException {
    rawProcess.await(); // returns immediately if process already complete
    return processFinished();
  }

  /**
   * Blocks until the command completes or the timeout is reached. If the timeout is reached before
   * the command finishes, {@link TimeoutException} is thrown. It returns the command result if
   * the result satisfies the command's success condition (by default a zero exit code), and throws
   * {@link CommandFailureException} otherwise.
   *
   * <p>If this thread is interrupted, an {@link InterruptedException} is thrown, but the <i>process
   * will continue running</i>. If you wish the process to be killed in this case, catch this
   * exception and call {@link CommandProcess#kill} on the running process.
   *
   * @throws CommandFailureException - if the result fails the command's success condition
   * @throws InterruptedException - if the execution is interrupted
   */
  @CanIgnoreReturnValue
  public final CommandResult await(long timeout, TimeUnit unit)
      throws CommandFailureException, InterruptedException, TimeoutException {
    if (rawProcess.await(timeout, unit)) {
      return processFinished();
    }
    throw new TimeoutException(
        String.format("%s did not complete after %d %s.", command, timeout, unit));
  }

  /**
   * A helper method for cleaning up once the process has finished. This method should only be
   * called once the caller knows the process has terminated (e.g. by calling {link #await}).
   *
   * @throws CommandFailureException if the command fails.
   * @throws InterruptedException if the raw process is interrupted.
   */
  private CommandResult processFinished()
      throws CommandFailureException, InterruptedException {
    // Double-checked locking to compute the result at most once.
    if (result == null) {
      synchronized (this) {
        if (result == null) {
          verify(!rawProcess.isAlive());
          int exitCode = rawProcess.await();
          // The process has ended, so there is no need to wait indefinitely for the stdin pump,
          // which may in theory be blocked or simply unbounded; just force stop it now.
          if (stdinPump.isPresent()) {
            stdinPump.get().stop();
          }
          // However, wait indefinitely for the stdout and stderr pumps before returning. The
          // default case of writing to a CapturingOutputStream will never block forever; if the
          // user provides a custom output stream, the burden is on them that it doesn't either.
          if (stdoutPump.isPresent()) {
            stdoutPump.get().await();
          }
          if (stderrPump.isPresent()) {
            stderrPump.get().await();
          }
          result = new CommandResult(exitCode, stdoutStream, stderrStream);
        }
      }
    }

    if (command.successCondition().apply(result)) {
      return result;
    } else {
      throw new CommandFailureException(command, result);
    }
  }

  /**
   * Sends a signal to terminate the process and returns immediately. Killing a process that has
   * already exited has no effect. To wait for the process to be killed, use {@code kill().await()}.
   *
   * <p>This method makes a best-effort attempt to read in any data already written to stdout or
   * stderr before the process is terminated. Reading output from a process as you kill it is
   * inherently racy, so if there's any output you expect to see it should be read explicitly via
   * {@link #stdoutStream}/{@link #stderrStream} before calling this method.
   *
   * <p><b>Implementation detail:</b> manual benchmarking suggests {@link Process} will buffer up to
   * 60KB internally, which this method should be able to retrieve.
   */
  @CanIgnoreReturnValue
  public final CommandProcess kill() {
    rawProcess.kill();
    return this;
  }

  /** Returns a new {@link InputStream} connected to the standard output of the process. */
  public final InputStream stdoutStream() {
    return stdoutStream.openInputStream();
  }

  /** Returns a new {@link InputStream} connected to the standard error of the process. */
  public final InputStream stderrStream() {
    return stderrStream.openInputStream();
  }

  /** Returns a new {@link Reader} connected to the standard output of the process. */
  public final Reader stdoutReader(Charset cs) {
    return new InputStreamReader(stdoutStream(), cs);
  }

  /** Returns a new {@link Reader} connected to the standard error of the process. */
  public final Reader stderrReader(Charset cs) {
    return new InputStreamReader(stderrStream(), cs);
  }

  /**
   * Returns a new {@link Reader} connected to the standard output of the process, assuming the
   * output uses UTF-8 encoding.
   */
  public final Reader stdoutReaderUtf8() {
    return stdoutReader(UTF_8);
  }

  /**
   * Returns a new {@link Reader} connected to the standard error of the process, assuming the
   * output uses UTF-8 encoding.
   */
  public final Reader stderrReaderUtf8() {
    return stderrReader(UTF_8);
  }

  @Override
  public final int hashCode() {
    // All implementations must use reference equality.
    return super.hashCode();
  }

  @Override
  public final boolean equals(Object o) {
    // All implementations must use reference equality.
    return super.equals(o);
  }

  @Override
  public final String toString() {
    return MoreObjects.toStringHelper(this)
        .add("command", command)
        .add("alive", isAlive())
        .toString();
  }

  /**
   * A low-level process API used to implement {@link CommandProcess}. This is a bare-bones
   * "strategy" object that executors use to create instances of the more user-friendly
   * CommandProcess type. The alternative to the strategy object design is a complicated combination
   * of constructor args and abstract hook methods on CommandProcess. Also, unlike hook methods, a
   * strategy object allows us to safely invoke these methods in the CommandProcess constructor, a
   * feature which CommandProcess uses to start a StdinPump, for example.
   */
  protected interface RawProcess {
    /**
     * Returns an output stream that writes to the standard input of the process; absent when the
     * process has redirected its stdin and, therefore, isn't reading input over a pipe.
     */
    Optional<OutputStream> stdinPipe();

    /**
     * Returns an input stream that reads from the standard output of the process; absent when the
     * process has redirected its stdout and, therefore, isn't writing output over a pipe.
     */
    Optional<InputStream> stdoutPipe();

    /**
     * Returns an input stream that reads from the standard error of the process; absent when the
     * process has redirected its stderr and, therefore, isn't writing error over a pipe.
     */
    Optional<InputStream> stderrPipe();

    /** Returns whether the process has not yet terminated. */
    boolean isAlive();

    /**
     * Waits for the process to terminate and returns its exit code.
     *
     * @throws InterruptedException - if the execution is interrupted
     */
    @CanIgnoreReturnValue
    int await() throws InterruptedException;

    /**
     * Waits up to timeout for the process to terminate, returning true if it did so.
     *
     * @throws InterruptedException - if the execution is interrupted
     */
    boolean await(long timeout, TimeUnit unit) throws InterruptedException;

    /** Sends a signal to terminate the process and returns immediately. */
    void kill();
  }
}
