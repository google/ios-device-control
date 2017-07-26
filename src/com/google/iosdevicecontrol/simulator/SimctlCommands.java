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

package com.google.iosdevicecontrol.simulator;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.iosdevicecontrol.command.Command.command;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.iosdevicecontrol.command.Command;
import com.google.iosdevicecontrol.command.CommandProcess;
import com.google.iosdevicecontrol.command.CommandStartException;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Abstracts the commands necessary for simulated devices that use xcrun simctl. */
final class SimctlCommands {
  private static final Command SIMCTL = command("xcrun", "simctl");
  private static final Command SIMULATOR =
      command(
          Paths.get(
              "/Applications/Xcode.app/Contents/Developer"
                  + "/Applications/Simulator.app/Contents/MacOS/Simulator"));

  static CommandProcess list() {
    return exec("list", "--json", "devices");
  }

  static CommandProcess shutdownAll() {
    return exec("shutdown", "all");
  }

  private final String udid;

  SimctlCommands(String udid) {
    this.udid = checkNotNull(udid);
  }

  CommandProcess getenv(String envVar) {
    return exec("getenv", udid, envVar);
  }

  CommandProcess install(String ipaPath) {
    return exec("install", udid, ipaPath);
  }

  CommandProcess uninstall(String bundleId) {
    return exec("uninstall", udid, bundleId);
  }

  CommandProcess launch(String bundleId, String... args) {
    return exec(
        simctl("launch", "--console", udid, bundleId)
            .withArgumentsAppended(ImmutableList.copyOf(args)));
  }

  CommandProcess screenshot(Path screenshotPath) {
    return exec("io", udid, "screenshot", "--type=png", screenshotPath.toString());
  }

  CommandProcess enumerate() {
    return exec("io", udid, "enumerate");
  }

  CommandProcess shutdown() {
    return exec("shutdown", udid);
  }

  CommandProcess syslog(Path syslogPath) {
    return exec(
        simctl("spawn", udid, "log", "stream", "--level=debug", "--system")
            .withStdoutTo(syslogPath));
  }

  /** Boot the device and open it in the simulator for debugging. */
  CommandProcess open() {
    return exec(SIMULATOR.withArguments("-CurrentDeviceUDID", udid));
  }

  /** Boot the device. */
  CommandProcess boot() {
    return exec("boot", udid);
  }

  CommandProcess erase() {
    return exec("erase", udid);
  }

  private static Command simctl(String... arguments) {
    return SIMCTL.withArgumentsAppended(ImmutableList.copyOf(arguments));
  }

  private static CommandProcess exec(String... arguments) {
    return exec(simctl(arguments));
  }

  private static CommandProcess exec(Command command) {
    try {
      return command.withEnvironment(ImmutableMap.of()).start();
    } catch (CommandStartException e) {
      throw new IllegalStateException("Has Xcode 8 been installed?", e);
    }
  }

  static final class ShellCommands {
    static CommandProcess lsof(String... args) {
      return exec(command("lsof", args));
    }

    static CommandProcess ps(String... args) {
      return exec(command("ps", args));
    }
  }
}
