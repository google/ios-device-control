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

package com.google.iosdevicecontrol;

import com.google.auto.value.AutoValue;
import com.google.common.base.Splitter;
import com.google.common.base.Suppliers;
import com.google.iosdevicecontrol.util.StringEnumMap;
import java.util.function.Supplier;

/** Model information of an iOS device. */
@AutoValue
public abstract class IosModel {
  private static final Splitter PRODUCT_CLASS_SPLITTER = Splitter.on(' ').limit(2);

  /** The class of device. */
  public enum DeviceClass {
    IPAD,
    IPHONE,
    IPOD;

    private static final StringEnumMap<DeviceClass> STRING_TO_DEVICE_CLASS =
        new StringEnumMap<>(DeviceClass.class);

    /**
     * Returns the device class for the specified string, e.g. returns <code>IPHONE</code> for the
     * string "iPhone".
     */
    public static DeviceClass forString(String s) {
      return STRING_TO_DEVICE_CLASS.get(s);
    }

    private final String string;

    private DeviceClass() {
      // e.g. "IPHONE" becomes "iPhone";
      string = "iP" + name().substring(2).toLowerCase();
    }

    @Override
    public String toString() {
      return string;
    }
  }

  /** CPU architecture of an iOS device. */
  public enum Architecture {
    ARM64,
    ARMV7,
    ARMV7F,
    ARMV7K,
    ARMV7S,
    I386,
    X86_64;

    private static final StringEnumMap<Architecture> STRING_TO_ARCHITECTURE =
        new StringEnumMap<>(Architecture.class);

    /**
     * Returns the architecture for the specified string, e.g. returns <code>ARMV7</code> for the
     * string "armv7".
     */
    public static Architecture forString(String s) {
      return STRING_TO_ARCHITECTURE.get(s);
    }

    private final String string;

    private Architecture() {
      string = name().toLowerCase();
    }

    @Override
    public String toString() {
      return string;
    }
  }

  public static Builder builder() {
    return new AutoValue_IosModel.Builder();
  }

  private final Supplier<DeviceClass> deviceClass =
      Suppliers.memoize(
          () ->
              DeviceClass.forString(PRODUCT_CLASS_SPLITTER.split(productName()).iterator().next()));

  /** Model identifier, e.g. "iPhone5,1". */
  public abstract String identifier();

  /** Product string of this model, e.g. "iPhone 5". */
  public abstract String productName();

  /** Architecture of the device, e.g. "arm64". */
  public abstract Architecture architecture();

  /** Device class of this model, e.g. "iPad". */
  public final DeviceClass deviceClass() {
    return deviceClass.get();
  }

  /** IosModel builder. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder identifier(String identifier);

    public abstract Builder productName(String productName);

    public abstract Builder architecture(Architecture architecture);

    /** Equivalent to <code>architecture(Architecture.fromString(archString))</code>. */
    public final Builder architecture(String archString) {
      return architecture(Architecture.forString(archString));
    }

    public abstract IosModel build();
  }
}
