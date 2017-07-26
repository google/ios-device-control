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

package com.google.iosdevicecontrol.real;

import static com.google.iosdevicecontrol.command.Command.command;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.iosdevicecontrol.command.Command;
import com.google.iosdevicecontrol.command.CommandExecutor;
import com.google.iosdevicecontrol.command.CommandProcess;
import com.google.iosdevicecontrol.command.CommandStartException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.function.UnaryOperator;

/** The binaries provided by libimobiledevice and friends. */
final class IdeviceCommands {
  static CommandProcess id(CommandExecutor executor, String... args) {
    return exec(executor, "idevice_id", ImmutableList.copyOf(args), UnaryOperator.identity());
  }

  static CommandProcess exec(Command command) {
    try {
      return command.start();
    } catch (CommandStartException e) {
      throw new IllegalStateException("Have the idevice commands been installed?", e);
    }
  }

  private final CommandExecutor executor;
  private final String udid;

  IdeviceCommands(CommandExecutor executor, String udid) {
    this.executor = executor;
    this.udid = udid;
  }

  CommandProcess apprunner(String... args) {
    return exec("idevice-app-runner", args);
  }

  CommandProcess date(String... args) {
    return exec("idevicedate", args);
  }

  CommandProcess diagnostics(String... args) {
    return exec("idevicediagnostics", args);
  }

  CommandProcess imagemounter(String... args) {
    return exec("ideviceimagemounter", args);
  }

  CommandProcess info(String... args) {
    return exec("ideviceinfo", args);
  }

  CommandProcess installer(String... args) {
    return exec("ideviceinstaller", args);
  }

  CommandProcess screenshot(String... args) {
    return exec("idevicescreenshot", args);
  }

  CommandProcess syslog(Path logPath, String... args) {
    return exec("idevicesyslog", args, c -> c.withStdoutTo(logPath));
  }

  CommandProcess crashreport(String... args) {
    return exec("idevicecrashreport", args);
  }

  private CommandProcess exec(String filename, String... args) {
    return exec(filename, args, UnaryOperator.identity());
  }

  private CommandProcess exec(String filename, String[] args, UnaryOperator<Command> transform) {
    ImmutableList<String> commandArgs =
        ImmutableList.<String>builder().add("-u").add(udid).addAll(Arrays.asList(args)).build();
    return exec(executor, filename, commandArgs, transform);
  }

  /**
   * Starts an idevice command and translates any raised IOException to an IllegalStateException. If
   * start raises IOException, the binary file probably does not exist or is not executable; i.e.
   * the idevice commands were not installed properly prior to using this library.
   */
  private static CommandProcess exec(
      CommandExecutor executor,
      String filename,
      List<String> commandArgs,
      UnaryOperator<Command> transform) {
    // TODO(user): Install idevice commands to temp on the fly.
    Path path = Paths.get("/usr/local/bin", filename);
    Command command =
        command(path)
            .withExecutor(executor)
            .withArguments(commandArgs)
            .withEnvironment(ImmutableMap.of());
    command = transform.apply(command);
    return exec(command);
  }
}
