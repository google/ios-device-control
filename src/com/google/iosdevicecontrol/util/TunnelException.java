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

import java.util.concurrent.Callable;

/**
 * An unchecked exception used to <i>temporarily</i> wrap a checked exception, so that it can be
 * thrown from a method or constructor that doesn't declare that exception type, and then extracted
 * in a surrounding context and handled or propagated as usual.
 */
public final class TunnelException extends RuntimeException {
  /**
   * Evaluate the result of the specified lambda, wrapping any checked exception thrown in a {@code
   * TunnelException}. Unchecked exceptions are rethrown unchanged.
   */
  public static <T> T tunnel(Callable<T> callback) {
    checkNotNull(callback);
    try {
      return callback.call();
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new TunnelException(e);
    }
  }

  private TunnelException(Exception e) {
    super("TunnelExceptions should always be unwrapped, so this message should never be seen.", e);
  }

  @Override
  public synchronized Exception getCause() {
    return (Exception) super.getCause();
  }

  private static ClassCastException exception(
      Throwable cause, String message, Object... formatArgs) {
    ClassCastException result = new ClassCastException(String.format(message, formatArgs));
    result.initCause(cause);
    return result;
  }

  @SafeVarargs
  private static void checkNoRuntimeExceptions(
      String methodName, Class<? extends Exception>... clazzes) {
    for (Class<? extends Exception> clazz : clazzes) {
      checkArgument(
          !RuntimeException.class.isAssignableFrom(clazz),
          "The cause of a TunnelException can never be a RuntimeException, "
              + "but %s argument was %s",
          methodName,
          clazz);
    }
  }

  /** Returns the underlying checked exception of the specified type. */
  public <X extends Exception> X getCauseAs(Class<X> exceptionClazz) {
    checkNotNull(exceptionClazz);
    checkNoRuntimeExceptions("getCause", exceptionClazz);
    if (exceptionClazz.isInstance(getCause())) {
      return exceptionClazz.cast(getCause());
    }
    throw exception(getCause(), "getCause(%s) doesn't match underlying exception", exceptionClazz);
  }
}
