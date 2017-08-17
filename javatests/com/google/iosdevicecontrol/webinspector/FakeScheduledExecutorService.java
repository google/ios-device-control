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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/** Fake implementation of {@link ScheduledExecutorService} for WebInspectorTest. */
final class FakeScheduledExecutorService implements ScheduledExecutorService {
  private final PriorityBlockingQueue<FixedDelayedFuture<?>> scheduledQueue =
      new PriorityBlockingQueue<>();
  private final AtomicLong nextSequenceId = new AtomicLong();
  private final AtomicLong currentTimeMillis = new AtomicLong();
  private volatile boolean running = true;

  @Override
  public boolean isShutdown() {
    return !running;
  }

  @Override
  public boolean isTerminated() {
    return isShutdown() && scheduledQueue.isEmpty();
  }

  @Override
  public void shutdown() {
    running = false;
  }

  @Override
  public List<Runnable> shutdownNow() {
    shutdown();
    List<Runnable> commands = new ArrayList<>(scheduledQueue);
    scheduledQueue.clear();
    return commands;
  }

  @Override
  public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
    checkState(!running);
    simulateSleepExecutingAllTasks(timeout, unit);
    return scheduledQueue.isEmpty();
  }

  @Override
  public ScheduledFuture<?> scheduleWithFixedDelay(
      Runnable command, long initialDelay, long delay, TimeUnit unit) {
    FixedDelayedFuture<?> future = new FixedDelayedFuture<>(command, initialDelay, delay, unit);
    scheduledQueue.add(future);
    return future;
  }

  /**
   * Simulate sleeping until the next scheduled task is set to run, then run the task. Does not run
   * any other commands.
   *
   * <p>Throws an unchecked throwable ({@code RuntimeException} or {@code Error}) if any {@code
   * Runnable} being run throws one, enabling this to work well with jmock expectations and junit
   * asserts.
   *
   * <p>Note that no throwables will be thrown for any submitted {@code Callable}, you need to
   * unwrap the {@code ExecutionException}.
   */
  void simulateSleepExecutingAtMostOneTask() {
    if (!scheduledQueue.isEmpty()) {
      FixedDelayedFuture<?> future = scheduledQueue.poll();
      future.run(); // adjusts clock
    }
  }

  private void simulateSleepExecutingAllTasks(long timeout, TimeUnit unit) {
    checkArgument(timeout >= 0);
    long stopTime = currentTimeMillis.get() + unit.toMillis(timeout);
    for (boolean done = false; !done; ) {
      long delay = (stopTime - currentTimeMillis.get());
      done = (delay < 0 || !simulateSleepExecutingAtMostOneTask(delay));
    }
  }

  private boolean simulateSleepExecutingAtMostOneTask(long timeout) {
    checkArgument(timeout >= 0);
    if (scheduledQueue.isEmpty()) {
      currentTimeMillis.addAndGet(timeout);
      return false;
    }

    FixedDelayedFuture<?> future = scheduledQueue.peek();
    long delay = future.getDelay(MILLISECONDS);
    if (delay > timeout) {
      // Next event is too far in the future; delay the entire time
      currentTimeMillis.addAndGet(timeout);
      return false;
    }

    scheduledQueue.poll();
    future.run(); // adjusts clock

    return true;
  }

  @Override
  public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
    throw new UnsupportedOperationException();
  }

  @Override
  public ScheduledFuture<?> scheduleAtFixedRate(
      Runnable command, long initialDelay, long period, TimeUnit unit) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T> Future<T> submit(Callable<T> task) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T> Future<T> submit(Runnable task, T result) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Future<?> submit(Runnable task) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
      throws InterruptedException {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T> List<Future<T>> invokeAll(
      Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
      throws InterruptedException {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
      throws InterruptedException, ExecutionException {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
      throws InterruptedException, ExecutionException, TimeoutException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void execute(Runnable command) {
    throw new UnsupportedOperationException();
  }

  private final class FixedDelayedFuture<T> implements ScheduledFuture<T>, Runnable {
    private final long sequenceId;
    private final Runnable command;
    private final long delay;

    private long timeToRun;
    private boolean cancelled;
    private boolean done;

    FixedDelayedFuture(Runnable command, long initialDelay, long delay, TimeUnit unit) {
      checkArgument(initialDelay >= 0);
      checkArgument(delay >= 0);
      sequenceId = nextSequenceId.getAndIncrement();
      this.command = command;
      this.delay = MILLISECONDS.convert(delay, unit);
      timeToRun = currentTimeMillis.get() + unit.toMillis(initialDelay);
    }

    @Override
    public long getDelay(TimeUnit unit) {
      return unit.convert(timeToRun - currentTimeMillis.get(), MILLISECONDS);
    }

    @Override
    public void run() {
      if (currentTimeMillis.get() < timeToRun) {
        currentTimeMillis.addAndGet(timeToRun - currentTimeMillis.get());
      }
      command.run();

      // Maybe reschedule the task for another iteration.
      if (!isCancelled()) {
        timeToRun = currentTimeMillis.get() + delay;
        scheduledQueue.add(this);
      }
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      cancelled = true;
      done = true;
      return scheduledQueue.remove(this);
    }

    @Override
    public boolean isCancelled() {
      return cancelled;
    }

    @Override
    public boolean isDone() {
      return done;
    }

    @Override
    public int compareTo(Delayed other) {
      if (other == this) { // compare zero ONLY if same object
        return 0;
      }
      FixedDelayedFuture<?> that = (FixedDelayedFuture<?>) other;
      long diff = timeToRun - that.timeToRun;
      if (diff < 0) {
        return -1;
      } else if (diff > 0) {
        return 1;
      } else if (sequenceId < that.sequenceId) {
        return -1;
      } else {
        return 1;
      }
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
      throw new UnsupportedOperationException();
    }

    @Override
    public T get(long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
      throw new UnsupportedOperationException();
    }
  }
}
