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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.iosdevicecontrol.command.Command.command;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.iosdevicecontrol.command.Command;
import com.google.iosdevicecontrol.command.CommandExecutor;
import com.google.iosdevicecontrol.command.CommandProcess;
import com.google.iosdevicecontrol.command.CommandStartException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

/**
 * Utility to execute the cfgutil subcommands against a device. The format of all command output is
 * plist XML.
 */
final class CfgutilCommands {
  private static final Path CFGUTIL = Paths.get("/usr/local/bin/cfgutil");

  /** Paths to the certificate and private key of a supervision identity. */
  @AutoValue
  abstract static class SupervisionIdentity {
    static SupervisionIdentity create(Path certPath, Path keyPath) {
      return new AutoValue_CfgutilCommands_SupervisionIdentity(certPath, keyPath);
    }

    abstract Path certificatePath();

    abstract Path privateKeyPath();
  }

  static CommandProcess list(CommandExecutor executor) {
    return exec(executor, ImmutableList.of("list"));
  }

  private final CommandExecutor executor;
  private final String ecid;
  private final Optional<SupervisionIdentity> supervisionId;

  CfgutilCommands(
      CommandExecutor executor, String ecid, Optional<SupervisionIdentity> supervisionId) {
    this.executor = checkNotNull(executor);
    this.ecid = checkNotNull(ecid);
    this.supervisionId = checkNotNull(supervisionId);
  }

  boolean isSupervised() {
    return supervisionId.isPresent();
  }

  CommandProcess get(String property) {
    return exec("get", property);
  }

  CommandProcess installProfile(String profilePath) {
    return execSupervised("install-profile", profilePath);
  }

  CommandProcess pair() {
    return execSupervised("pair");
  }

  CommandProcess removeProfile(String pathOrIdentifier) {
    return execSupervised("remove-profile", pathOrIdentifier);
  }

  private CommandProcess exec(String subcommand, String... args) {
    ImmutableList<String> arguments =
        ImmutableList.<String>builder().add("-e", ecid).add(subcommand).add(args).build();
    return exec(executor, arguments);
  }

  private CommandProcess execSupervised(String subcommand, String... args) {
    checkState(
        isSupervised(),
        "must set a supervision identity in the device host to use `cfgutil %s`",
        subcommand);
    ImmutableList<String> arguments =
        ImmutableList.<String>builder()
            .add("-e", ecid)
            .add("-C", supervisionId.get().certificatePath().toString())
            .add("-K", supervisionId.get().privateKeyPath().toString())
            .add(subcommand)
            .add(args)
            .build();
    return exec(executor, arguments);
  }

  /**
   * Starts an cfgutil command and translates any raised CommandStartException to an
   * IllegalStateException. If start raises IllegalStateException, the Apple Configurator 2
   * automation tools are probably not installed.
   */
  private static CommandProcess exec(CommandExecutor executor, List<String> arguments) {
    Command command =
        command(CFGUTIL)
            .withExecutor(executor)
            .withArguments("--format", "plist")
            .withArgumentsAppended(arguments)
            .withEnvironment(ImmutableMap.of());
    try {
      return command.start();
    } catch (CommandStartException e) {
      throw new IllegalStateException(
          "Have the Apple Configurator automation tools been installed?", e);
    }
  }
}
