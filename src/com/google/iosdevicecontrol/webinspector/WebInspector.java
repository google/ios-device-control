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
import static com.google.common.base.Preconditions.checkState;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.dd.plist.NSDictionary;
import com.google.common.annotations.VisibleForTesting;
import com.google.iosdevicecontrol.util.FluentLogger;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.iosdevicecontrol.util.EllipsisFormat;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** A web inspector. */
public final class WebInspector implements AutoCloseable {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final EllipsisFormat MESSAGE_FORMAT = new EllipsisFormat(2500);

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
    return new WebInspector(socket, Executors.newSingleThreadScheduledExecutor());
  }

  private final InspectorSocket socket;
  private final ScheduledExecutorService executor;
  private final AtomicReference<Future<?>> receiveFuture = new AtomicReference<>();

  @VisibleForTesting
  public WebInspector(InspectorSocket socket, ScheduledExecutorService executor) {
    this.socket = checkNotNull(socket);
    this.executor = checkNotNull(executor);
  }

  /** Starts listening to inspector messages from the inspector. */
  public void startListening(Consumer<InspectorMessage> listener) {
    checkNotNull(listener);
    checkState(!isStarted());
    Runnable receiveTask = () -> receiveMessage(listener);
    receiveFuture.set(executor.scheduleWithFixedDelay(receiveTask, 0, 50, MILLISECONDS));
  }

  /** Sends a message to the web inspector. */
  public void sendMessage(InspectorMessage message) throws IOException {
    checkState(isStarted());
    checkState(!isClosed());
    socket.sendMessage(message.toPlist());
    logger.atInfo().log("Message sent: %s", formatMessage(message));
  }

  /**
   * Receives a message from the inspector socket and notifies all observers. This is run inside a
   * separate thread, so all errors are caught and logged.
   */
  private void receiveMessage(Consumer<InspectorMessage> listener) {
    try {
      // Receive a plist over the socket. On EOF, if the thread is marked interrupted, that means
      // the socket was intentionally closed by the #close method; otherwise do a full close now.
      Optional<NSDictionary> plistDict = socket.receiveMessage();
      if (!plistDict.isPresent()) {
        if (!isClosed()) {
          logger.atSevere().log("Web inspector closed unexpectedly.");
          close();
        }
        return;
      }

      // Convert the plist to a structured message and notify all observers of it.
      InspectorMessage message = InspectorMessage.fromPlist(plistDict.get());
      logger.atInfo().log("Message received: %s", formatMessage(message));
      listener.accept(message);
    } catch (Throwable e) {
      logger.atWarning().withCause(e).log();
    }
  }

  /**
   * Stops receiving messages over the socket and closes it. Any futures that have not yet been
   * completed are immediately completed with an IOException.
   */
  @Override
  public void close() throws IOException {
    checkState(isStarted());
    try {
      // Canceling the future marks the future done and the messenger "closed".
      // If it can't be cancelled, it must have terminated prematurely, so raise an exception.
      if (!receiveFuture.get().cancel(false)) {
        Futures.getChecked(receiveFuture.get(), IOException.class);
      }
    } finally {
      try {
        MoreExecutors.shutdownAndAwaitTermination(executor, 5, SECONDS);
      } finally {
        socket.close();
      }
    }
  }

  private boolean isStarted() {
    return receiveFuture.get() != null;
  }

  private boolean isClosed() {
    return isStarted() && receiveFuture.get().isDone();
  }

  private static Object formatMessage(InspectorMessage message) {
    return new Object() {
      @Override
      public String toString() {
        return MESSAGE_FORMAT.format(message.toString());
      }
    };
  }
}
