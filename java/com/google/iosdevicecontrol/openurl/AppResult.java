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

package com.google.iosdevicecontrol.openurl;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import java.net.InetAddress;
import java.util.Optional;

/** A result of running the openURL app. */
public abstract class AppResult {
  private final String rawOutput;

  private AppResult(String rawOutput) {
    this.rawOutput = rawOutput;
  }

  public final String rawOutput() {
    return rawOutput;
  }

  /** Result of asking the app to open a url. */
  public static final class OpenUrlResult extends AppResult {
    private final boolean success;

    @VisibleForTesting
    public OpenUrlResult(String rawOutput, boolean success) {
      super(rawOutput);
      this.success = success;
    }

    public boolean success() {
      return success;
    }
  }

  /** Result of asking the app to check the wifi connection. */
  public static final class CheckWifiResult extends AppResult {
    private final Optional<WifiConnection> connection;

    @VisibleForTesting
    public CheckWifiResult(String rawOutput, Optional<WifiConnection> connection) {
      super(rawOutput);
      this.connection = connection;
    }

    public Optional<WifiConnection> connection() {
      return connection;
    }
  }

  /** Information about the WiFi connection of the device. */
  @AutoValue
  public abstract static class WifiConnection {
    public static WifiConnection create(InetAddress ip, String ssid) {
      return new AutoValue_AppResult_WifiConnection(ip, ssid);
    }

    public abstract InetAddress ip();

    public abstract String ssid();
  }
}
