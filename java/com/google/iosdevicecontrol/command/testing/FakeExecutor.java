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

import com.google.common.collect.ImmutableList;
import com.google.iosdevicecontrol.command.Command;
import com.google.iosdevicecontrol.command.CommandExecutor;
import com.google.iosdevicecontrol.command.CommandStartException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * An executor that creates a new running {@link FakeProcess} on each call to {@link #start}, and
 * saves the processes it started in a FIFO queue for later inspection by a test. This class is
 * thread-safe, so it may be used as a fake in concurrent applications.
 */
public class FakeExecutor implements CommandExecutor {
  /** Returns a FakeExector whoses processes immediately exit with the specified exit code. */
  public static FakeExecutor immediatelyExits(final int exitCode) {
    return new FakeExecutor() {
      @Override
      public FakeProcess start(Command command) throws CommandStartException {
        FakeProcess process = super.start(command);
        process.setTerminated(exitCode);
        return process;
      }
    };
  }

  /**
   * Returns a FakeExector whoses processes immediately exit with the specified exit code, stdout,
   * and stderr.
   */
  public static FakeExecutor immediatelyExits(
      final int exitCode, final String stdout, final String stderr) {
    return new FakeExecutor() {
      @Override
      public FakeProcess start(Command command) throws CommandStartException {
        FakeProcess process = super.start(command);
        process.writeStdoutUtf8(stdout);
        process.writeStderrUtf8(stderr);
        process.setTerminated(exitCode);
        return process;
      }
    };
  }

  private final Queue<FakeProcess> started = new ConcurrentLinkedQueue<>();

  @Override
  public FakeProcess start(Command command) throws CommandStartException {
    FakeProcess process = FakeProcess.start(command);
    started.add(process);
    return process;
  }

  /**
   * Retrieves and removes the earliest started process in the queue (the head of the queue).
   *
   * @throws NoSuchElementException - if no process if left in the queue.
   */
  public FakeProcess dequeueProcess() {
    return started.remove();
  }

  /** Retrieves and removes all processes in the queue, in the order they were started. */
  public ImmutableList<FakeProcess> dequeueAllProcesses() {
    ImmutableList.Builder<FakeProcess> processes = ImmutableList.builder();
    for (Iterator<FakeProcess> i = started.iterator(); i.hasNext(); ) {
      processes.add(i.next());
      i.remove();
    }
    return processes.build();
  }
}
