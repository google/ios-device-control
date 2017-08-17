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

import static com.google.common.base.Preconditions.checkState;

import com.dd.plist.NSDictionary;
import com.google.common.collect.ImmutableList;
import com.google.iosdevicecontrol.webinspector.InspectorMessage;
import com.google.iosdevicecontrol.webinspector.InspectorSocket;
import java.io.IOException;
import java.util.Iterator;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Fake implementation of {@link InspectorSocket} that maintains a queue of all messages sent to the
 * inspector and a queue of all messages to be received by the inspector.
 */
public final class FakeInspectorSocket implements InspectorSocket {
  private volatile boolean closed;
  private final Queue<InspectorMessage> messagesSent = new ConcurrentLinkedQueue<>();
  private final BlockingQueue<InspectorMessage> messagesToReceive = new LinkedBlockingQueue<>();

  @Override
  public void sendMessage(NSDictionary message) throws IOException {
    if (closed) {
      throw new IOException("socket closed");
    }
    messagesSent.offer(InspectorMessage.fromPlist(message));
  }

  @Override
  public Optional<NSDictionary> receiveMessage() throws IOException {
    if (closed) {
      return Optional.empty();
    }
    try {
      return Optional.of(messagesToReceive.take().toPlist());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException(e);
    }
  }

  /** Dequeues and returns all the messages sent to the inspector. */
  public ImmutableList<InspectorMessage> dequeueMessagesSent() {
    ImmutableList.Builder<InspectorMessage> messages = ImmutableList.builder();
    for (Iterator<InspectorMessage> i = messagesSent.iterator(); i.hasNext(); ) {
      messages.add(i.next());
      i.remove();
    }
    return messages.build();
  }

  /** Enqueues a message to be returned by {@link #receiveMessage}. */
  public void enqueueMessageToReceive(InspectorMessage message) {
    checkState(!closed);
    checkState(messagesToReceive.add(message));
  }

  @Override
  public void close() throws IOException {
    closed = true;
  }

  public boolean isClosed() {
    return closed;
  }
}
