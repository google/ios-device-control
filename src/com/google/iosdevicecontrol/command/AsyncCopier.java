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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Supplier;
import com.google.iosdevicecontrol.util.FluentLogger;
import com.google.common.io.ByteStreams;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.common.util.concurrent.Uninterruptibles;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;

/** Copies an InputStream to an OutputStream asynchronously. */
final class AsyncCopier {
  /** Injectable strategy for performing a synchronous copy. */
  @VisibleForTesting
  interface CopyStrategy {
    void copy(InputStream from, OutputStream to) throws IOException;
  }

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @VisibleForTesting
  static final CopyStrategy REAL_COPY_STRATEGY =
      new CopyStrategy() {
        @Override
        public void copy(InputStream from, OutputStream to) throws IOException {
          ByteStreams.copy(from, to);
        }
      };

  @VisibleForTesting
  static final ExecutorService realExecutorService =
      Executors.newCachedThreadPool(
          new ThreadFactoryBuilder().setNameFormat("async-copy-%d").setDaemon(true).build());

  /**
   * Starts an asynchronous copy from the input stream to the output stream. The returned copier
   * assumes responsibility for closing the streams when the copy is complete.
   */
  static AsyncCopier start(InputStream from, OutputStream to, Supplier<Level> ioExceptionLogLevel) {
    return new AsyncCopier(from, to, ioExceptionLogLevel, REAL_COPY_STRATEGY, realExecutorService);
  }

  private final InputStream source;
  private final OutputStream sink;
  private final Supplier<Level> ioExceptionLogLevel;
  private final CopyStrategy copyStrategy;
  private final Future<?> copyFuture;

  private final CountDownLatch copyStarted = new CountDownLatch(1);
  private final CountDownLatch copyTerminated = new CountDownLatch(1);

  @VisibleForTesting
  AsyncCopier(
      InputStream source,
      OutputStream sink,
      Supplier<Level> ioExceptionLogLevel,
      CopyStrategy copyStrategy,
      ExecutorService executorService) {
    this.source = source;
    this.sink = sink;
    this.ioExceptionLogLevel = ioExceptionLogLevel;
    this.copyStrategy = copyStrategy;

    // Submit the copy task and wait uninterruptibly, but very briefly, for it to actually start.
    copyFuture = executorService.submit(new Runnable() {
      @Override public void run() {
        copy();
      }
    });
    Uninterruptibles.awaitUninterruptibly(copyStarted);
  }

  private void copy() {
    copyStarted.countDown();
    try {
      try {
        copyStrategy.copy(source, sink);
      } catch (IOException e) {
        logger.at(ioExceptionLogLevel.get()).withCause(e).log();
      } finally {
        closeStreams();
      }
    } catch (RuntimeException e) {
      logger.atSevere().withCause(e).log();
    } finally {
      copyTerminated.countDown();
    }
  }

  /** Waits for the asynchronous copy to complete. */
  void await() throws InterruptedException {
    copyTerminated.await();
  }

  /** Stops the asynchronous copy and blocks until it terminates. */
  void stop() throws InterruptedException {
    // Unfortunately, if the streams are blocked, there is no provided method to unblock them.
    // That said, interrupting and then closing the streams appears to work for all streams used in
    // practice and likely for all conceivably valid streams in existence, so we do that.
    // http://bugs.java.com/bugdatabase/view_bug.do?bug_id=4514257
    // http://stackoverflow.com/a/4182848

    // Interrupt the copyFuture, in case the input source interruptible, like PipedInputStream.
    copyFuture.cancel(true);

    // Close the streams, which should unblock all other kinds of streams.
    closeStreams();

    await();
  }

  private void closeStreams() {
    try {
      try {
        source.close();
      } finally {
        sink.close();
      }
    } catch (IOException e) {
      logger.at(ioExceptionLogLevel.get()).withCause(e).log();
    }
  }
}
