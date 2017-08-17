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

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.Serializable;
import java.util.Objects;
import java.util.concurrent.Callable;
import javax.annotation.Nullable;

/**
 * Static utilities for {@link CheckedCallable}s.
 */
public final class CheckedCallables {
  /**
   * Returns a a {@link CheckedCallable} that always returns <code>instance</code>.
   */
  public static <V, X extends Exception> CheckedCallable<V, X> returning(V instance) {
    return new CheckedCallableReturning<>(instance);
  }

  private static class CheckedCallableReturning<V, X extends Exception>
      implements CheckedCallable<V, X>, Serializable {
    private final V instance;

    private CheckedCallableReturning(V instance) {
      this.instance = instance;
    }

    @Override
    public V call() {
      return instance;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (obj instanceof CheckedCallableReturning) {
        CheckedCallableReturning<?, ?> that = (CheckedCallableReturning<?, ?>) obj;
        return Objects.equals(instance, that.instance);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(instance);
    }

    @Override
    public String toString() {
      return "CheckedCallables.returning(" + instance + ")";
    }

    private static final long serialVersionUID = 0;
  }

  /**
   * Wraps a {@link Callable} as a {@link CheckedCallable}.
   */
  @SuppressWarnings("unchecked")
  public static <V> CheckedCallable<V, Exception> fromCallable(Callable<? extends V> c) {
    return c instanceof CheckedCallable
        ? (CheckedCallable<V, Exception>) c
        : new CheckedCallableFromCallable<V>(c);
  }

  private static class CheckedCallableFromCallable<V>
      implements CheckedCallable<V, Exception>, Serializable {
    private final Callable<? extends V> delegate;

    private CheckedCallableFromCallable(Callable<? extends V> delegate) {
      this.delegate = checkNotNull(delegate);
    }

    @Override
    public V call() throws Exception {
      return delegate.call();
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (obj instanceof CheckedCallableFromCallable) {
        CheckedCallableFromCallable<?> that = (CheckedCallableFromCallable<?>) obj;
        return delegate.equals(that.delegate);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(delegate);
    }

    @Override
    public String toString() {
      return "CheckedCallables.fromCallable(" + delegate + ")";
    }

    private static final long serialVersionUID = 0;
  }

  /**
   * Wraps a {@link VoidCallable} as a {@link CheckedCallable}.
   */
  public static <X extends Exception> CheckedCallable<Void, X> fromVoidCallable(
      VoidCallable<? extends X> c) {
    return new CheckedCallableFromVoidCallable<X>(c);
  }

  private static class CheckedCallableFromVoidCallable<X extends Exception>
      implements CheckedCallable<Void, X>, Serializable {
    private final VoidCallable<? extends X> delegate;

    private CheckedCallableFromVoidCallable(VoidCallable<? extends X> delegate) {
      this.delegate = checkNotNull(delegate);
    }

    @Override
    public Void call() throws X {
      delegate.call();
      return null;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (obj instanceof CheckedCallableFromVoidCallable) {
        CheckedCallableFromVoidCallable<?> that = (CheckedCallableFromVoidCallable<?>) obj;
        return delegate.equals(that.delegate);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(delegate);
    }

    @Override
    public String toString() {
      return "CheckedCallables.fromVoidCallable(" + delegate + ")";
    }

    private static final long serialVersionUID = 0;
  }

  /**
   * Returns a {@link CheckedCallable} which caches the instance retrieved or exception thrown
   * during the first call and returns that value or throws that exception on subsequent calls.
   */
  public static <V, X extends Exception> CheckedCallable<V, X> memoize(CheckedCallable<V, X> c) {
    return c instanceof MemoizingCheckedCallable ? c : new MemoizingCheckedCallable<V, X>(c);
  }

  private static class MemoizingCheckedCallable<V, X extends Exception>
      implements CheckedCallable<V, X>, Serializable {
    private final CheckedCallable<V, X> delegate;

    private transient volatile boolean initialized;
    // The "value" and "exception" fields do not need to be volatile; their visibility piggy-backs
    // on volatile read of "initialized".
    private transient V value;
    private transient Exception exception;

    private MemoizingCheckedCallable(CheckedCallable<V, X> delegate) {
      this.delegate = checkNotNull(delegate);
    }

    @Override
    @SuppressWarnings("unchecked")
    public V call() throws X {
      // Double Checked Locking.
      if (!initialized) {
        synchronized (this) {
          if (!initialized) {
            try {
              value = delegate.call();
            } catch (Exception e) {
              exception = e;
            }
            initialized = true;
          }
        }
      }

      if (exception == null) {
        return value;
      } else {
        // The exception either be of generic type X or a RuntimeException, but a generic cast will
        // succeed in either case due to type erasure.
        throw (X) exception;
      }
    }

    @Override
    public String toString() {
      return "CheckedCallables.memoize(" + delegate + ")";
    }

    private static final long serialVersionUID = 0;
  }

  private CheckedCallables() {}
}
