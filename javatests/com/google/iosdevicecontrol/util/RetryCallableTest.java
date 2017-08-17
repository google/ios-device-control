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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assert_;
import static com.google.iosdevicecontrol.util.RetryCallable.retry;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link com.google.iosdevicecontrol.util.RetryCallable}. */
@RunWith(JUnit4.class)
public class RetryCallableTest {
  @Test
  public void testRetryWithCheckedCallable() throws Exception {
    boolean result =
        retry(
                new CheckedCallable<Boolean, Exception>() {
                  @Override
                  public Boolean call() {
                    return true;
                  }
                })
            .call();
    assertThat(result).isTrue();

    RetryCallable<Boolean, Exception> failingRetry =
        retry(
            new CheckedCallable<Boolean, Exception>() {
              @Override
              public Boolean call() throws Exception {
                throw new Exception();
              }
            });
    try {
      failingRetry.call();
      assert_().fail();
    } catch (Exception expected) {
    }
  }

  @Test
  public void testRetryWithVoidCallable() throws Exception {
    retry(
            new VoidCallable<Exception>() {
              @Override
              public void call() {}
            })
        .call();

    RetryCallable<Void, Exception> failingRetry =
        retry(
            new VoidCallable<Exception>() {
              @Override
              public void call() throws Exception {
                throw new Exception();
              }
            });
    try {
      failingRetry.call();
      assert_().fail();
    } catch (Exception expected) {
    }
  }

  @Test
  public void testRetrySucceedsAfterFailedAttempts() throws Exception {
    final List<Exception> exceptions = new ArrayList<>();

    retry(
            new VoidCallable<Exception>() {
              @Override
              public void call() throws Exception {
                if (exceptions.size() < RetryCallable.DEFAULT_MAX_ATTEMPTS - 1) {
                  addAndThrowException(new Exception(), exceptions);
                }
              }
            })
        .call();

    assertThat(exceptions).isNotEmpty();
  }

  @Test
  public void testUncheckedExceptionInTask() {
    final List<Exception> exceptions = new ArrayList<>();

    RetryCallable<Void, RuntimeException> failingRetry =
        retry(
            new VoidCallable<RuntimeException>() {
              @Override
              public void call() {
                addAndThrowException(new RuntimeException(), exceptions);
              }
            });
    try {
      failingRetry.call();
      assert_().fail();
    } catch (RuntimeException expected) {
      assertThat(exceptions).containsExactly(expected);
    }
  }

  @Test
  public void testCheckedExceptionInExceptionHandler() {
    final List<Exception> exceptions = new ArrayList<>();
    final Exception callException = new Exception();
    final Exception handlerException = new Exception();

    RetryCallable<Void, Exception> failingRetry =
        retry(
                new VoidCallable<Exception>() {
                  @Override
                  public void call() throws Exception {
                    addAndThrowException(callException, exceptions);
                  }
                })
            .withExceptionHandler(
                new ExceptionHandler<Exception>() {
                  @Override
                  public void handle(Exception exception) throws Exception {
                    addAndThrowException(handlerException, exceptions);
                  }
                });

    try {
      failingRetry.call();
      assert_().fail();
    } catch (Exception expected) {
      assertThat(exceptions).containsExactly(callException, handlerException);
      // The exception from call() is propagated and the handler exception is suppressed.
      assertThat(expected).isEqualTo(callException);
      assertThat(expected.getSuppressed()).asList().containsExactly(handlerException);
    }
  }

  @Test
  public void testUncheckedExceptionInExceptionHandler() throws Exception {
    final List<Exception> exceptions = new ArrayList<>();
    final Exception callException = new Exception();
    final RuntimeException handlerException = new RuntimeException();

    RetryCallable<Void, Exception> failingRetry =
        retry(
                new VoidCallable<Exception>() {
                  @Override
                  public void call() throws Exception {
                    addAndThrowException(callException, exceptions);
                  }
                })
            .withExceptionHandler(
                new ExceptionHandler<Exception>() {
                  @Override
                  public void handle(Exception exception) {
                    addAndThrowException(handlerException, exceptions);
                  }
                });

    try {
      failingRetry.call();
      assert_().fail();
    } catch (RuntimeException expected) {
      assertThat(exceptions).containsExactly(callException, handlerException);
      // The exception from handler is propagated and no exception is suppressed.
      assertThat(expected).isEqualTo(handlerException);
      assertThat(expected.getSuppressed()).asList().isEmpty();
    }
  }

  @Test
  public void testInterruptedBetweenRetries() {
    final List<Exception> exceptions = new ArrayList<>();
    final Exception callException = new Exception();

    RetryCallable<Void, Exception> failingRetry =
        retry(
                new VoidCallable<Exception>() {
                  @Override
                  public void call() throws Exception {
                    addAndThrowException(callException, exceptions);
                  }
                })
            .withExceptionHandler(
                new ExceptionHandler<Exception>() {
                  @Override
                  public void handle(Exception exception) {
                    Thread.currentThread().interrupt();
                  }
                });

    try {
      failingRetry.call();
      assert_().fail();
    } catch (Exception expected) {
      assertThat(Thread.interrupted()).isTrue();
      assertThat(exceptions).containsExactly(callException);
      // The exception from call is propagated with a suppressed interrupted exception.
      assertThat(expected).isEqualTo(callException);
      assertThat(expected.getSuppressed()).asList().hasSize(1);
      assertThat(expected.getSuppressed()[0]).isInstanceOf(InterruptedException.class);
    }
  }

  private static <X extends Exception> void addAndThrowException(
      X failure, List<Exception> failures) throws X {
    failures.add(failure);
    throw failure;
  }
}
