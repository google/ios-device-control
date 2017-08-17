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

package com.google.iosdevicecontrol.util;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.google.common.base.Verify;
import com.google.common.util.concurrent.Uninterruptibles;
import org.joda.time.Duration;

/**
 * A {@code Callable} providing blocking retry capabilities.
 *
 * <p>There is a <code>RetryingCallable</code> in Guava labs, and this class has several deliberate
 * differences with that class, most importantly:
 * <ul>
 * <li>This is stateless and can be reused, whereas an instance of the Guava class retains the
 * state of how many times it has been called and cannot be reused.
 * <li>This deliberately does not retry on {@link RuntimeException}s; instead choosing to rethrow
 * them immediately, because they should only be non-recoverable programming errors.
 * <li>This rethrows the original exception type and uses suppressed exceptions over a separate
 * <code>RetryException<code> that then often has to be wrapped into the original exception type.
 * <li>This uses the immutable builder pattern instead of the (mutable) builder pattern, because
 * this is more concise and there is no signifant performance benefit to the mutability here.
 * <li>
 * </ul>
 */
public final class RetryCallable<V, X extends Exception> implements CheckedCallable<V, X> {
  /** Default number of maximum attempts: 3. */
  public static final int DEFAULT_MAX_ATTEMPTS = 3;

  /**
   * Creates a callable that retries the specified task up to the default number of attempts, with
   * no delay between attempts and a noop exception handler.
   */
  public static <V, X extends Exception> RetryCallable<V, X> retry(
      CheckedCallable<? extends V, ? extends X> task) {
    return new RetryCallable<>(
        task, DEFAULT_MAX_ATTEMPTS, Duration.ZERO, false, ExceptionHandler.NOOP);
  }

  /**
   * Creates a callable that retries the specified task up to the default number of attempts, with
   * no delay between attempts and a noop exception handler.
   */
  public static <X extends Exception> RetryCallable<Void, X> retry(VoidCallable<? extends X> task) {
    return retry(CheckedCallables.fromVoidCallable(task));
  }

  private final CheckedCallable<? extends V, ? extends X> task;
  private final int maxAttempts;
  private final Duration delay;
  private final boolean delayedFirstAttempt;
  private final ExceptionHandler<? super X> exceptionHandler;

  private RetryCallable(
      CheckedCallable<? extends V, ? extends X> task,
      int maxAttempts,
      Duration delay,
      boolean delayedFirstAttempt,
      ExceptionHandler<? super X> exceptionHandler) {
    this.task = checkNotNull(task);
    checkArgument(maxAttempts > 0);
    this.maxAttempts = maxAttempts;
    this.delay = checkNotNull(delay);
    this.delayedFirstAttempt = delayedFirstAttempt;
    this.exceptionHandler = checkNotNull(exceptionHandler);
  }

  /**
   * Returns a callable equivalent to this one but with the specified number of maximum attempts.
   */
  public RetryCallable<V, X> withMaxAttempts(int maxAttempts) {
    return new RetryCallable<>(task, maxAttempts, delay, delayedFirstAttempt, exceptionHandler);
  }

  /** Returns a callable equivalent to this one but with the specified delay between attempts. */
  public RetryCallable<V, X> withDelay(Duration delay) {
    return new RetryCallable<>(task, maxAttempts, delay, delayedFirstAttempt, exceptionHandler);
  }

  /** Returns a callable equivalent to this one but with the delay before the first attempt. */
  public RetryCallable<V, X> withDelayedFirstAttempt() {
    return new RetryCallable<>(task, maxAttempts, delay, delayedFirstAttempt, exceptionHandler);
  }

  /** Returns a callable equivalent to this one but with the specified exception handler. */
  public RetryCallable<V, X> withExceptionHandler(ExceptionHandler<? super X> exceptionHandler) {
    return new RetryCallable<>(task, maxAttempts, delay, delayedFirstAttempt, exceptionHandler);
  }

  /**
   * Repeatedly attempts a task up to the specified (or default) number of maximum attempts,
   * handling each checked exception thrown by the task with the specified (or default) exception
   * handler, and then waiting the specified (or default) delay between attempts.
   *
   * <p>If the maximum number of attempts has been reached, the first exception is thrown with all
   * remaining exceptions added as suppressed exceptions to the original. If the exception handler
   * throws a checked exception, it is added as a suppressed exception to the original, and the
   * original exception is thrown immediately with no further retries attempted. If this thread is
   * interrupted, an {@link InterruptedException} is added as suppressed to the original, and the
   * original exception is thrown immediately with no further retries attempted. If the task or the
   * exception handler throws an unchecked exception, this unchecked exception is propagated
   * immediately with no retry.
   */
  @Override
  public V call() throws X {
    if (delayedFirstAttempt) {
      Uninterruptibles.sleepUninterruptibly(delay.getMillis(), MILLISECONDS);
    }

    X cause = null;
    for (int i = 0; i < maxAttempts; i++) {
      try {
        return task.call();
      } catch (RuntimeException e) {
        // Propagate unchecked exceptions immediately.
        throw e;
      } catch (Exception e) {
        // If it's a checked exception, it's necessarily of type X.
        @SuppressWarnings("unchecked")
        X failure = (X) e;
        if (cause == null) {
          cause = failure;
        } else {
          cause.addSuppressed(failure);
        }

        // Handle the failure.
        try {
          exceptionHandler.handle(failure);
        } catch (RuntimeException e2) {
          // Propagate unchecked exceptions immediately.
          throw e2;
        } catch (Exception e2) {
          // A checked exception is marked suppressed and the call ends immediately.
          if (!e2.equals(failure)) {
            cause.addSuppressed(e2);
          }
          break;
        }

        // Sleep for the specified delay.
        try {
          Thread.sleep(delay.getMillis());
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          cause.addSuppressed(ie);
          break;
        }
      }
    }

    throw Verify.verifyNotNull(cause);
  }
}
