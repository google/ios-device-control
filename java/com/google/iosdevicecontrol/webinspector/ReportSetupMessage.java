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

import com.google.auto.value.AutoValue;
import com.google.common.base.Optional;

/**
 * A reportSetup message.
 *
 * <p>Example:
 *
 * <pre>{@code
 * <?xml version="1.0" encoding="UTF-8"?>
 * <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN"
 *     "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
 * <plist version="1.0">
 * <dict>
 *   <key>__selector</key>
 *   <string>_rpc_reportSetup:</string>
 *   <key>__argument</key>
 *   <dict>
 *     <key>WIRSimulatorBuildKey</key>
 *     <string>11D167</string>
 *     <key>WIRSimulatorNameKey</key>
 *     <string>iPhone Simulator</string>
 *     <key>WIRSimulatorProductVersionKey</key>
 *     <string>7.1</string>
 *   </dict>
 * </dict>
 * </plist>
 * }</pre>
 */
@AutoValue
public abstract class ReportSetupMessage extends InspectorMessage {
  /** Returns a new builder. */
  public static Builder builder() {
    return new AutoValue_ReportSetupMessage.Builder();
  }

  /** A builder for creating reportSetup messages. */
  @AutoValue.Builder
  public abstract static class Builder extends InspectorMessage.Builder {
    @Override
    public final Builder simulatorBuild(String simulatorBuild) {
      return optionalSimulatorBuild(simulatorBuild);
    }

    @Override
    public final Builder simulatorName(String simulatorName) {
      return optionalSimulatorName(simulatorName);
    }

    @Override
    public final Builder simulatorProductVersion(String simulatorProductVersion) {
      return optionalSimulatorProductVersion(simulatorProductVersion);
    }

    @Override
    public abstract ReportSetupMessage build();

    abstract Builder optionalSimulatorBuild(String simulatorBuild);

    abstract Builder optionalSimulatorName(String simulatorName);

    abstract Builder optionalSimulatorProductVersion(String simulatorProductVersion);
  }

  public abstract Optional<String> optionalSimulatorBuild();

  public abstract Optional<String> optionalSimulatorName();

  public abstract Optional<String> optionalSimulatorProductVersion();

  @Override
  final String simulatorBuild() {
    return fromOptional(optionalSimulatorBuild());
  }

  @Override
  final String simulatorName() {
    return fromOptional(optionalSimulatorName());
  }

  @Override
  final String simulatorProductVersion() {
    return fromOptional(optionalSimulatorProductVersion());
  }

  @Override
  public MessageSelector selector() {
    return MessageSelector.REPORT_SETUP;
  }
}
