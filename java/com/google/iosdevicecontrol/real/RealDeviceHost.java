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
import static com.google.iosdevicecontrol.util.TunnelException.tunnel;

import com.google.common.base.Splitter;
import com.google.iosdevicecontrol.util.TunnelException;
import com.google.common.collect.ImmutableSet;
import com.google.iosdevicecontrol.command.Command;
import com.google.iosdevicecontrol.command.CommandExecutor;
import com.google.iosdevicecontrol.command.CommandFailureException;
import com.google.iosdevicecontrol.command.CommandProcess;
import com.google.iosdevicecontrol.IosDevice;
import com.google.iosdevicecontrol.IosDeviceHost;
import com.google.iosdevicecontrol.real.CfgutilCommands.SupervisionIdentity;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Implementation of {@link IosDeviceHost} backed by the libimobiledevice library.
 *
 * <p>A RealDeviceHost can only be initialized once, as protection against over-exercising physical
 * iOS devices with a barrage of commands firing from multiple hosts.
 */
public final class RealDeviceHost implements IosDeviceHost {
  private static final Path XCODE_ROOT_IMAGES_DIR =
      Paths.get(
          "/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneOS.platform/DeviceSupport");
  private static final Splitter LINE_SPLITTER = Splitter.on('\n').trimResults().omitEmptyStrings();

  private static final AtomicBoolean initialized = new AtomicBoolean();

  /** Configuration of the real device host. */
  public static final class Configuration {
    private final DevDiskImages devDiskImages;
    private final CommandExecutor executor;
    private final Optional<SupervisionIdentity> supervisionId;

    private Configuration(
        DevDiskImages devDiskImages,
        CommandExecutor executor,
        Optional<SupervisionIdentity> supervisionId) {
      this.devDiskImages = checkNotNull(devDiskImages);
      this.executor = checkNotNull(executor);
      this.supervisionId = checkNotNull(supervisionId);
    }

    public Configuration withExecutor(CommandExecutor executor) {
      return new Configuration(devDiskImages, executor, supervisionId);
    }

    public Configuration withSupervisionIdentity(Path certPath, Path keyPath) {
      return new Configuration(
          devDiskImages, executor, Optional.of(SupervisionIdentity.create(certPath, keyPath)));
    }

    public RealDeviceHost initialize() {
      checkState(!initialized.getAndSet(true), "RealDeviceHost already initialized");
      return new RealDeviceHost(this);
    }
  }

  public static Configuration withDeveloperDiskImagesFromXcode() {
    return withDeveloperDiskImagesFrom(XCODE_ROOT_IMAGES_DIR);
  }

  public static Configuration withDeveloperDiskImagesFrom(Path rootImagesDir) {
    return new Configuration(
        DevDiskImages.inDirectory(rootImagesDir), Command.NATIVE_EXECUTOR, Optional.empty());
  }

  private final Configuration configuration;
  private final Map<String, IosDevice> udid2Device = new ConcurrentHashMap<>();

  private RealDeviceHost(Configuration configuration) {
    this.configuration = checkNotNull(configuration);
  }

  @Override
  public ImmutableSet<IosDevice> connectedDevices() throws IOException {
    String devicesList = await(IdeviceCommands.id(configuration.executor, "-l"));
    try {
      return LINE_SPLITTER
          .splitToList(devicesList)
          .stream()
          .map(u -> udid2Device.computeIfAbsent(u, udid -> tunnel(() -> toDevice(udid))))
          .collect(ImmutableSet.toImmutableSet());
    } catch (TunnelException e) {
      throw e.getCauseAs(IOException.class);
    }
  }

  private IosDevice toDevice(String udid) throws IOException {
    IdeviceCommands idevice = new IdeviceCommands(configuration.executor, udid);
    CfgutilCommands cfgutil = toCfgutil(idevice);
    return new RealDeviceImpl(udid, idevice, cfgutil, configuration.devDiskImages);
  }

  private CfgutilCommands toCfgutil(IdeviceCommands idevice) throws IOException {
    // Using --simple means no pairing with the device is required.
    String ecidDecimal = await(idevice.info("--simple", "-k", "UniqueChipID")).trim();
    String ecidHex = Long.toHexString(Long.parseLong(ecidDecimal));
    return new CfgutilCommands(configuration.executor, ecidHex, configuration.supervisionId);
  }

  private static String await(CommandProcess process) throws IOException {
    try {
      return process.await().stdoutStringUtf8();
    } catch (CommandFailureException e) {
      throw new IOException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException(e);
    }
  }
}
