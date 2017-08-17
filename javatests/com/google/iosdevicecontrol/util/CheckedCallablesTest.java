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

import com.google.common.testing.EqualsTester;
import java.util.concurrent.Callable;
import junit.framework.TestCase;

/**
 * Tests for CheckedCallables.
 */
public class CheckedCallablesTest extends TestCase {
  public void testReturningReturnsSameInstance() throws Exception {
    Object toReturn = new Object();
    CheckedCallable<Object, Exception> objectReturner = CheckedCallables.returning(toReturn);
    assertThat(objectReturner.call()).isSameAs(toReturn);
    assertThat(objectReturner.call()).isSameAs(toReturn); // idempotent
  }

  public void testReturningReturnsNull() throws Exception {
    CheckedCallable<Integer, Exception> nullReturner = CheckedCallables.returning(null);
    assertThat(nullReturner.call()).isNull();
  }

  public void testReturningEquals() {
    new EqualsTester()
        .addEqualityGroup(CheckedCallables.returning("foo"), CheckedCallables.returning("foo"))
        .addEqualityGroup(CheckedCallables.returning("bar"))
        .testEquals();
  }

  public void testFromCallableReturnsSameInstance() throws Exception {
    final Object toReturn = new Object();
    Callable<Object> callable =
        new Callable<Object>() {
          @Override
          public Object call() {
            return toReturn;
          }
        };
    CheckedCallable<Object, Exception> checkedWrapper = CheckedCallables.fromCallable(callable);
    assertThat(checkedWrapper.call()).isSameAs(toReturn);
  }

  public void testFromCallableThrowsSameException() {
    final Exception toThrow = new Exception();
    Callable<Object> callable =
        new Callable<Object>() {
          @Override
          public Object call() throws Exception {
            throw toThrow;
          }
        };
    CheckedCallable<Object, Exception> checkedWrapper = CheckedCallables.fromCallable(callable);
    try {
      checkedWrapper.call();
      fail();
    } catch (Exception thrown) {
      assertThat(thrown).isSameAs(toThrow);
    }
  }

  public void testFromCallableEquals() {
    Callable<Object> foo =
        new Callable<Object>() {
          @Override
          public String call() throws Exception {
            return null;
          }
        };
    Callable<Object> bar =
        new Callable<Object>() {
          @Override
          public String call() throws Exception {
            return null;
          }
        };
    new EqualsTester()
        .addEqualityGroup(CheckedCallables.fromCallable(foo), CheckedCallables.fromCallable(foo))
        .addEqualityGroup(CheckedCallables.fromCallable(bar))
        .testEquals();
  }

  public void testFromVoidCallableThrowsSameException() {
    final Exception toThrow = new Exception();
    VoidCallable<Exception> callable =
        new VoidCallable<Exception>() {
          @Override
          public void call() throws Exception {
            throw toThrow;
          }
        };
    CheckedCallable<Void, Exception> checkedWrapper = CheckedCallables.fromVoidCallable(callable);
    try {
      checkedWrapper.call();
      fail();
    } catch (Exception thrown) {
      assertThat(thrown).isSameAs(toThrow);
    }
  }

  public void testFromVoidCallableEquals() {
    VoidCallable<Exception> foo =
        new VoidCallable<Exception>() {
          @Override
          public void call() throws Exception {
            throw new Exception("foo");
          }
        };
    VoidCallable<Exception> bar =
        new VoidCallable<Exception>() {
          @Override
          public void call() throws Exception {
            throw new Exception("bar");
          }
        };
    new EqualsTester()
        .addEqualityGroup(
            CheckedCallables.fromVoidCallable(foo), CheckedCallables.fromVoidCallable(foo))
        .addEqualityGroup(CheckedCallables.fromVoidCallable(bar))
        .testEquals();
  }

  public void testMemoizeReturnValue() throws Exception {
    CheckedCallable<Integer, Exception> counting =
        new CheckedCallable<Integer, Exception>() {
          private int count = 0;

          @Override
          public Integer call() throws Exception {
            return count++;
          }
        };
    CheckedCallable<Integer, Exception> memoized = CheckedCallables.memoize(counting);
    assertThat(memoized.call()).isEqualTo(0);
    assertThat(counting.call()).isEqualTo(1);
    assertThat(memoized.call()).isEqualTo(0);
  }

  public void testMemoizeException() throws Exception {
    CheckedCallable<Object, IntegerException> counting =
        new CheckedCallable<Object, IntegerException>() {
          private int count = 0;

          @Override
          public Integer call() throws IntegerException {
            throw new IntegerException(count++);
          }
        };
    CheckedCallable<Object, IntegerException> memoized = CheckedCallables.memoize(counting);
    assertThat(exceptionCount(memoized)).isEqualTo(0);
    assertThat(exceptionCount(counting)).isEqualTo(1);
    assertThat(exceptionCount(memoized)).isEqualTo(0);
  }

  public void testMemoizeRedundantly() {
    CheckedCallable<Object, Exception> callable = CheckedCallables.returning(null);
    CheckedCallable<Object, Exception> memoized = CheckedCallables.memoize(callable);
    assertThat(CheckedCallables.memoize(memoized)).isSameAs(memoized);
  }

  private static class IntegerException extends Exception {
    private final int value;

    private IntegerException(int value) {
      super(Integer.toString(value));
      this.value = value;
    }
  }

  private static int exceptionCount(CheckedCallable<?, IntegerException> callable) {
    try {
      callable.call();
      throw new AssertionError();
    } catch (IntegerException e) {
      return e.value;
    }
  }
}
