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
import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.common.collect.ImmutableMap;
import java.util.stream.Stream;

/** Immutable map from each enum value string to its value. */
public final class StringEnumMap<E extends Enum<E>> {
  private final ImmutableMap<String, E> stringToValue;

  /** Constructs an immutable map to every value in the specified enum class. */
  public StringEnumMap(Class<E> enumClass) {
    E[] values = enumClass.getEnumConstants();
    stringToValue = Stream.of(values).collect(toImmutableMap(v -> v.toString(), v -> v));
  }

  /**
   * Returns the enum value with the given string representation.
   *
   * @throws IllegalArgumentException - if there is no such enum value.
   */
  public E get(String string) {
    E value = stringToValue.get(string);
    checkArgument(value != null, "No value for string %s", string);
    return value;
  }
}
