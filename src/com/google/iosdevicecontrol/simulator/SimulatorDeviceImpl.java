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
import static com.google.common.base.Preconditions.checkState;
import static com.google.iosdevicecontrol.util.TunnelException.tunnel;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.dd.plist.NSDictionary;
import com.google.iosdevicecontrol.util.TunnelException;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MoreCollectors;
import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import com.google.iosdevicecontrol.command.CommandFailureException;
import com.google.iosdevicecontrol.command.CommandProcess;
import com.google.iosdevicecontrol.command.CommandResult;
import com.google.iosdevicecontrol.IosAppBundleId;
import com.google.iosdevicecontrol.IosAppInfo;
import com.google.iosdevicecontrol.IosAppProcess;
import com.google.iosdevicecontrol.IosDeviceException;
import com.google.iosdevicecontrol.IosDeviceResource;
import com.google.iosdevicecontrol.IosModel;
import com.google.iosdevicecontrol.IosVersion;
import com.google.iosdevicecontrol.util.CheckedCallable;
import com.google.iosdevicecontrol.util.CheckedCallables;
import com.google.iosdevicecontrol.util.PlistParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Implementation of {@link SimulatorDevice} for Simulator backed by simctl */
final class SimulatorDeviceImpl implements SimulatorDevice {
  private static final boolean DEBUG = false;
  private static final String DEVICE_TYPE_PREFIX = "com.apple.CoreSimulator.SimDeviceType.";

  private final String udid;
  private final IosVersion version;
  private final SimctlCommands simctl;
  private final AtomicBoolean systemLoggerStarted = new AtomicBoolean(false);

  private final CheckedCallable<String, IOException> getDeviceType =
      CheckedCallables.memoize(this::readDeviceType);

  SimulatorDeviceImpl(String udid, IosVersion version) {
    this.udid = udid;
    this.version = version;
    this.simctl = new SimctlCommands(udid);
  }

  @Override
  public String udid() {
    return udid;
  }

  @Override
  public boolean isResponsive() {
    try {
      return SimulatorDeviceHost.INSTANCE.bootedDevices().contains(this);
    } catch (IOException e) {
      return false;
    }
  }

  @Override
  public void erase() throws IosDeviceException {
    shutdown();
    await(simctl.erase());
  }

  @Override
  public IosModel model() throws IosDeviceException {
    try {
      return SimulatorDeviceHost.INSTANCE.getModel(getDeviceType.call());
    } catch (IOException e) {
      throw new IosDeviceException(this, e);
    }
  }

  private String readDeviceType() throws IOException {
    Path deviceInfoPath =
        Paths.get(
            System.getProperty("user.home"),
            "Library/Developer/CoreSimulator/Devices",
            udid,
            "device.plist");
    NSDictionary resultDict = (NSDictionary) PlistParser.fromPath(deviceInfoPath);
    // The device type string takes the form of:
    // "com.apple.CoreSimulator.SimDeviceType.[DeviceType]"
    String deviceType = resultDict.get("deviceType").toString();
    checkArgument(deviceType.startsWith(DEVICE_TYPE_PREFIX));
    return deviceType.substring(DEVICE_TYPE_PREFIX.length());
  }

  @Override
  public IosVersion version() throws IosDeviceException {
    return version;
  }

  @Override
  public ImmutableSet<IosAppInfo> listApplications() throws IosDeviceException {
    try {
      return ImmutableSet.<IosAppInfo>builder()
          .addAll(SimulatorDeviceHost.listSystemApps(version.productVersion()))
          .addAll(userApps())
          .build();
    } catch (IOException e) {
      throw new IosDeviceException(this, e);
    }
  }

  private ImmutableSet<IosAppInfo> userApps() throws IOException {
    Path appPath =
        Paths.get(
            System.getProperty("user.home"),
            "Library/Developer/CoreSimulator/Devices",
            udid,
            "data/Containers/Bundle/Application");
    if (!Files.exists(appPath)) {
      return ImmutableSet.of();
    }
    try {
      return MoreFiles.listFiles(appPath)
          .stream()
          .map(
              p ->
                  tunnel(
                      () ->
                          MoreFiles.listFiles(p)
                              .stream()
                              .filter(a -> MoreFiles.getFileExtension(a).equals("app"))
                              .collect(MoreCollectors.onlyElement())))
          .map(a -> tunnel(() -> IosAppInfo.readFromPath(a)))
          .collect(toImmutableSet());
    } catch (TunnelException e) {
      throw e.getCauseAs(IOException.class);
    }
  }

  @Override
  public boolean isApplicationInstalled(IosAppBundleId bundleId) throws IosDeviceException {
    return listApplications().stream().anyMatch(a -> a.bundleId().equals(bundleId));
  }

  @Override
  public void installApplication(Path ipaOrAppPath) throws IosDeviceException {
    try {
      if (Files.isDirectory(ipaOrAppPath)) {
        await(simctl.install(ipaOrAppPath.toString()));
      } else {
        Path tmpDir = Files.createTempDirectory("app");
        try {
          unzipFile(ipaOrAppPath, tmpDir);
          Path appPath =
              tmpDir
                  .resolve("Payload")
                  .resolve(MoreFiles.getNameWithoutExtension(ipaOrAppPath) + ".app");
          await(simctl.install(appPath.toString()));
        } finally {
          MoreFiles.deleteRecursively(tmpDir, RecursiveDeleteOption.ALLOW_INSECURE);
        }
      }
    } catch (IOException e) {
      throw new IosDeviceException(this, e);
    }
  }

  private void unzipFile(Path zipPath, Path targetDirectory) throws IOException {
    try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
      for (Enumeration<? extends ZipEntry> entries = zipFile.entries();
          entries.hasMoreElements(); ) {
        ZipEntry entry = entries.nextElement();
        Path targetFile = targetDirectory.resolve(entry.getName());
        if (entry.isDirectory()) {
          Files.createDirectories(targetFile);
        } else {
          Files.createDirectories(targetFile.getParent());
          try (InputStream in = zipFile.getInputStream(entry)) {
            Files.copy(in, targetFile);
          }
        }
      }
    }
  }

  @Override
  public void uninstallApplication(IosAppBundleId bundleId) throws IosDeviceException {
    await(simctl.uninstall(bundleId.toString()));
  }

  @Override
  public IosAppProcess runApplication(IosAppBundleId bundleId, String... args)
      throws IosDeviceException {
    return new SimulatorAppProcess(this, simctl.launch(bundleId.toString(), args));
  }

  @Override
  public IosDeviceResource startSystemLogger(Path logPath) throws IosDeviceException {
    checkState(!systemLoggerStarted.getAndSet(true), "System logger has already been started.");
    CommandProcess syslog = simctl.syslog(logPath);
    return new IosDeviceResource(this) {
      @Override
      public void close() throws IosDeviceException {
        checkState(systemLoggerStarted.getAndSet(false), "System logger has already been stopped.");
        await(syslog.kill(), 0, 143, 255);
      }
    };
  }

  @Override
  public void pullCrashLogs(Path directory) throws IosDeviceException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void clearCrashLogs() throws IosDeviceException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void restart() throws IosDeviceException {
    // Don't have to check if device is running, shutdown will exit gracefully if it isn't.
    shutdown();
    startup();
  }

  @Override
  public byte[] takeScreenshot() throws IosDeviceException {
    try {
      Path screenshotPath = Files.createTempFile("screenshot", ".out");
      try {
        await(simctl.screenshot(screenshotPath));
        return Files.readAllBytes(screenshotPath);
      } finally {
        Files.deleteIfExists(screenshotPath);
      }
    } catch (IOException e) {
      throw new IosDeviceException(this, e);
    }
  }

  @Override
  public void shutdown() throws IosDeviceException {
    await(simctl.shutdown(), 0, 163);
  }

  @Override
  public void startup() throws IosDeviceException {
    if (DEBUG) {
      if (isResponsive()) {
        return;
      }
      simctl.open();
    } else {
      await(simctl.boot(), 0, 163);
    }
    while (!isResponsive() || !isScreenshottable()) {
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IosDeviceException(this, e);
      }
    }
  }

  /**
   * Checks if the device is ready to take a screenshot. For a brief period after a device is booted
   * the device does not have io capabilities.
   */
  private boolean isScreenshottable() throws IosDeviceException {
    return await(simctl.enumerate()).stdoutStringUtf8().contains("IOSurface port");
  }

  private CommandResult awaitResult(CommandProcess command) throws IosDeviceException {
    try {
      return command.await();
    } catch (CommandFailureException e) {
      return e.result();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IosDeviceException(this, e);
    }
  }

  /**
   * Waits for a process to terminate and returns the result of the execution, allowing specified
   * exit codes through. If the exit code is not one of the expected values, this method throws an
   * IosDeviceException.
   */
  private CommandResult await(CommandProcess process, Integer... expectedExitCodes)
      throws IosDeviceException {
    CommandResult result = awaitResult(process);
    Set<Integer> expectedExitCodeSet =
        expectedExitCodes.length == 0 ? ImmutableSet.of(0) : ImmutableSet.copyOf(expectedExitCodes);
    if (!expectedExitCodeSet.contains(result.exitCode())) {
      throw new IosDeviceException(this, "Unexpected exit code in result: " + result);
    }
    return result;
  }
}
