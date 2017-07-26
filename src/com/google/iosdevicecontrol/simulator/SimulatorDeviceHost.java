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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.iosdevicecontrol.util.TunnelException.tunnel;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.dd.plist.NSArray;
import com.dd.plist.NSDictionary;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.iosdevicecontrol.util.TunnelException;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MoreCollectors;
import com.google.common.io.MoreFiles;
import com.google.iosdevicecontrol.command.CommandFailureException;
import com.google.iosdevicecontrol.command.CommandProcess;
import com.google.iosdevicecontrol.command.CommandResult;
import com.google.iosdevicecontrol.IosAppInfo;
import com.google.iosdevicecontrol.IosDevice;
import com.google.iosdevicecontrol.IosDeviceHost;
import com.google.iosdevicecontrol.IosModel;
import com.google.iosdevicecontrol.IosModel.Architecture;
import com.google.iosdevicecontrol.IosVersion;
import com.google.iosdevicecontrol.simulator.SimctlCommands.ShellCommands;
import com.google.iosdevicecontrol.util.CheckedCallable;
import com.google.iosdevicecontrol.util.CheckedCallables;
import com.google.iosdevicecontrol.util.JavaxJson;
import com.google.iosdevicecontrol.util.PlistParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;
import javax.json.JsonArray;
import javax.json.JsonObject;

/** Implementation of {@link IosDeviceHost} that returns simulator devices. */
public class SimulatorDeviceHost implements IosDeviceHost {
  public static final SimulatorDeviceHost INSTANCE = new SimulatorDeviceHost();

  static final Path CRASH_LOG_PATH =
      checkNotNull(Paths.get(System.getProperty("user.home"), "Library/Logs/DiagnosticReports"));

  private static final Pattern GENERATION_PATTERN =
      Pattern.compile("\\((\\d+)\\p{Alpha}{2} generation\\)");

  private static final ConcurrentMap<String, ImmutableSet<IosAppInfo>> runtime2SystemApps =
      new ConcurrentHashMap<>();

  private final BiMap<String, IosDevice> udid2Device = HashBiMap.create();
  private final BiMap<String, IosModel> deviceType2Model = HashBiMap.create();

  /**
   * Device list does not change during runtime, so it can be lazily evaluated on subsequent calls.
   */
  private final CheckedCallable<ImmutableSet<IosDevice>, IOException> connectedDevices =
      CheckedCallables.memoize(() -> collectDevices(Predicates.alwaysTrue()));

  private SimulatorDeviceHost() {}

  /**
   * Clear crash logs of all devices on the host (including those that are not currently booted).
   */
  public void clearCrashLogs() throws IOException {
    try {
      MoreFiles.listFiles(CRASH_LOG_PATH)
          .forEach(
              p ->
                  tunnel(
                      () -> {
                        Files.delete(p);
                        return null;
                      }));
    } catch (TunnelException e) {
      throw e.getCauseAs(IOException.class);
    } catch (NoSuchFileException e) {
      // The folder wouldn't have been created if there hasn't been a crash yet
    }
  }

  /**
   * Move the crash logs of all devices into the given directory (including those that are not
   * currently booted).
   */
  void pullCrashLogs(Path directory) throws IOException {
    try {
      MoreFiles.listFiles(CRASH_LOG_PATH)
          .forEach(
              p ->
                  tunnel(
                      () -> {
                        Files.move(p, directory.resolve(p.getFileName()));
                        return null;
                      }));
    } catch (TunnelException e) {
      throw e.getCauseAs(IOException.class);
    } catch (NoSuchFileException e) {
      // The folder wouldn't have been created if there hasn't been a crash yet
    }
  }

  /** Returns the device that is currently using the inspector port or empty if no such device. */
  public Optional<IosDevice> deviceOnInspectorPort() throws IOException {
    // 27753 is the port that the Simulator uses for the inspector.
    // Exit code 1 means that the specified port isn't in use.
    String pid = await(ShellCommands.lsof("-ti", ":27753"), 0, 1).stdoutStringUtf8();
    if (pid.isEmpty()) {
      return Optional.empty();
    }
    String processesOutput =
        await(ShellCommands.ps("-p", pid.trim(), "-o", "command")).stdoutStringUtf8();
    return connectedDevices().stream().filter(s -> processesOutput.contains(s.udid())).findFirst();
  }

  public void shutdownAllDevices() throws IOException {
    await(SimctlCommands.shutdownAll());
  }

  @Override
  public ImmutableSet<IosDevice> connectedDevices() throws IOException {
    return connectedDevices.call();
  }

  public ImmutableSet<IosDevice> bootedDevices() throws IOException {
    return collectDevices(data -> data.getString("state").equals("Booted"));
  }

  private ImmutableSet<IosDevice> collectDevices(Predicate<JsonObject> test) throws IOException {
    String devices = await(SimctlCommands.list()).stdoutStringUtf8();
    JsonObject jsonOutput = JavaxJson.parseObject(devices).getJsonObject("devices");
    return jsonOutput
        .entrySet()
        .stream()
        .filter(e -> e.getKey().startsWith("iOS"))
        .flatMap(
            e -> {
              IosVersion version = getVersion(e.getKey().substring(4));
              return ((JsonArray) e.getValue())
                  .stream()
                  .map(v -> (JsonObject) v)
                  .filter(test)
                  .map(data -> toDevice(version, data));
            })
        .collect(toImmutableSet());
  }

  /** Build a single version for simulated devices that share a runtime. */
  private IosVersion getVersion(String productVersion) {
    Path systemVersionFile =
        runtimeRootPath(productVersion).resolve("System/Library/CoreServices/SystemVersion.plist");
    NSDictionary resultDict = (NSDictionary) PlistParser.fromPath(systemVersionFile);
    String buildVersion = resultDict.get("ProductBuildVersion").toString();
    return IosVersion.builder().buildVersion(buildVersion).productVersion(productVersion).build();
  }

  private IosDevice toDevice(IosVersion version, JsonObject data) {
    return udid2Device.computeIfAbsent(
        data.getString("udid"), u -> new SimulatorDeviceImpl(u, version));
  }

  static ImmutableSet<IosAppInfo> listSystemApps(String productVersion) throws IOException {
    try {
      return runtime2SystemApps.computeIfAbsent(
          productVersion,
          r ->
              tunnel(
                  () -> {
                    Path appsDir = runtimeRootPath(productVersion).resolve("Applications");
                    return MoreFiles.listFiles(appsDir)
                        .stream()
                        .filter(p -> Files.exists(p.resolve("Info.plist")))
                        .map(a -> tunnel(() -> IosAppInfo.readFromPath(a)))
                        .collect(toImmutableSet());
                  }));
    } catch (TunnelException e) {
      throw e.getCauseAs(IOException.class);
    }
  }

  /**
   * Depending on how the runtime is installed, the applications folder and system version
   * information can be located in one of two paths. The SDK path is used if it exists, otherwise
   * the simruntime directory is created on boot if it does not exist and is then used.
   */
  private static Path runtimeRootPath(String productVersion) {
    Path runtimeRoot =
        Paths.get(
            "/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform"
                + "/Developer/SDKs/iPhoneSimulator"
                + productVersion
                + ".sdk");
    if (!Files.exists(runtimeRoot)) {
      runtimeRoot =
          Paths.get(
              "/Library/Developer/CoreSimulator/Profiles/Runtimes/iOS "
                  + productVersion
                  + ".simruntime/Contents/Resources/RuntimeRoot");
    }
    return runtimeRoot;
  }

  IosModel getModel(String deviceType) throws IOException {
    try {
      return deviceType2Model.computeIfAbsent(deviceType, p -> tunnel(() -> toModel(deviceType)));
    } catch (TunnelException e) {
      throw e.getCauseAs(IOException.class);
    }
  }

  private static IosModel toModel(String deviceType) throws IOException {
    checkArgument(deviceType.matches("[-\\w]*"));
    Path profileInfoBasePath =
        Paths.get(
            "/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/"
                + "Developer/Library/CoreSimulator/Profiles/DeviceTypes");
    Path deviceTypePath =
        MoreFiles.listFiles(profileInfoBasePath)
            .stream()
            // The directory name that matches a given device type is the same as the name from the
            // device.plist file, with the hyphens replaced with spaces, hyphens, parentheses or
            // periods
            .filter(
                p -> MoreFiles.getNameWithoutExtension(p).replaceAll("\\W", "-").equals(deviceType))
            .collect(MoreCollectors.onlyElement());
    String rawProductName = MoreFiles.getNameWithoutExtension(deviceTypePath);
    String productName = GENERATION_PATTERN.matcher(rawProductName).replaceFirst("$1");

    Path profilePath = deviceTypePath.resolve("Contents/Resources/profile.plist");
    NSDictionary profileDict = (NSDictionary) PlistParser.fromPath(profilePath);
    String identifier = profileDict.get("modelIdentifier").toString();

    NSArray supportedArchs = (NSArray) profileDict.get("supportedArchs");
    // The supported architecture can either be just i386 or i386 and x86_64. The
    // actual architecture will be x86_64 if its supported, or i386 otherwise.
    Architecture architecture =
        supportedArchs.containsObject(Architecture.X86_64.toString())
            ? Architecture.X86_64
            : Architecture.I386;
    return IosModel.builder()
        .identifier(identifier)
        .productName(productName)
        .architecture(architecture)
        .build();
  }

  private CommandResult awaitResult(CommandProcess command) throws IOException {
    try {
      return command.await();
    } catch (CommandFailureException e) {
      return e.result();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException(e);
    }
  }

  /**
   * Waits for a process to terminate and returns the result of the execution, allowing specified
   * exit codes through. If the exit code is not one of the expected values, this method throws an
   * IosDeviceException.
   */
  private CommandResult await(CommandProcess process, Integer... expectedExitCodes)
      throws IOException {
    CommandResult result = awaitResult(process);
    Set<Integer> expectedExitCodeSet =
        expectedExitCodes.length == 0 ? ImmutableSet.of(0) : ImmutableSet.copyOf(expectedExitCodes);
    if (!expectedExitCodeSet.contains(result.exitCode())) {
      throw new IOException("Unexpected exit code in result: " + result);
    }
    return result;
  }
}
