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

package com.google.iosdevicecontrol.testing;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.iosdevicecontrol.IosAppBundleId;
import com.google.iosdevicecontrol.IosAppInfo;
import com.google.iosdevicecontrol.IosAppProcess;
import com.google.iosdevicecontrol.IosDevice;
import com.google.iosdevicecontrol.IosDeviceException;
import com.google.iosdevicecontrol.IosDeviceResource;
import com.google.iosdevicecontrol.IosDeviceSocket;
import com.google.iosdevicecontrol.IosModel;
import com.google.iosdevicecontrol.IosModel.Architecture;
import com.google.iosdevicecontrol.IosVersion;
import com.google.iosdevicecontrol.real.ConfigurationProfile;
import com.google.iosdevicecontrol.real.RealDevice;
import com.google.iosdevicecontrol.simulator.SimulatorDevice;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Fake implementation of {@link IosDevice}. */
public abstract class FakeIosDevice implements IosDevice {
  private final String udid;
  private boolean responsive = false;
  private IosModel model =
      IosModel.builder()
          .architecture(Architecture.ARM64)
          .identifier("iPhone5,1")
          .productName("iPhone 5")
          .build();
  private IosVersion version =
      IosVersion.builder().buildVersion("12H321").productVersion("8.4.1").build();
  private final BiMap<IosAppBundleId, IosAppInfo> applications = HashBiMap.create();
  private IosAppProcess appOutput = new FakeIosAppProcess(this);
  private final AtomicBoolean systemLoggerStarted = new AtomicBoolean(false);
  private byte[] inspectorInput = new byte[0];

  private FakeIosDevice() {
    udid = UUID.randomUUID().toString();
  }

  @Override
  public String udid() {
    return udid;
  }

  @Override
  public boolean isResponsive() {
    return responsive;
  }

  @Override
  public IosModel model() throws IosDeviceException {
    checkResponsive();
    return model;
  }

  @Override
  public IosVersion version() throws IosDeviceException {
    checkResponsive();
    return version;
  }

  @Override
  public ImmutableSet<IosAppInfo> listApplications() throws IosDeviceException {
    checkResponsive();
    return ImmutableSet.copyOf(applications.values());
  }

  @Override
  public boolean isApplicationInstalled(IosAppBundleId bundleId) throws IosDeviceException {
    checkResponsive();
    return applications.containsKey(bundleId);
  }

  @Override
  public void installApplication(Path ipaPath) throws IosDeviceException {
    checkResponsive();
    IosAppInfo appInfo;
    try {
      appInfo = IosAppInfo.readFromPath(ipaPath);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    applications.put(appInfo.bundleId(), appInfo);
  }

  @Override
  public void uninstallApplication(IosAppBundleId bundleId) throws IosDeviceException {
    checkResponsive();
    applications.remove(bundleId);
  }

  @Override
  public IosAppProcess runApplication(IosAppBundleId bundleId, String... args)
      throws IosDeviceException {
    checkResponsive();
    return appOutput;
  }

  @Override
  public IosDeviceResource startSystemLogger(Path syslogPath) throws IosDeviceException {
    checkState(!systemLoggerStarted.getAndSet(true), "System logger already started.");
    checkResponsive();
    try {
      Files.createFile(syslogPath);
    } catch (IOException e) {
      throw new IosDeviceException(this, e);
    }
    return new IosDeviceResource(this) {
      @Override
      public void close() {
        checkState(systemLoggerStarted.getAndSet(false), "System logger already stopped.");
      }
    };
  }

  @Override
  public void pullCrashLogs(Path directory) throws IosDeviceException {
    checkResponsive();
  }

  @Override
  public void clearCrashLogs() throws IosDeviceException {
    checkResponsive();
  }

  @Override
  public IosDeviceSocket openWebInspectorSocket() throws IosDeviceException {
    return new FakeIosDeviceSocket(this, inspectorInput);
  }

  @Override
  public void restart() throws IosDeviceException {
    checkResponsive();
    // TODO(user): Allow the restart duration and potential for timeout to be configured.
  }

  @Override
  public byte[] takeScreenshot() throws IosDeviceException {
    return new byte[] {};
  }

  public FakeIosDevice setResponsive(boolean responsive) {
    this.responsive = responsive;
    return this;
  }

  public FakeIosDevice setModel(IosModel model) {
    this.model = checkNotNull(model);
    return this;
  }

  public FakeIosDevice setVersion(IosVersion version) {
    this.version = checkNotNull(version);
    return this;
  }

  public FakeIosDevice setApplicationOutput(IosAppProcess appOutput) {
    this.appOutput = checkNotNull(appOutput);
    return this;
  }

  public FakeIosDevice setInspectorInput(byte[] inspectorInput) {
    this.inspectorInput = inspectorInput;
    return this;
  }

  private void checkResponsive() throws IosDeviceException {
    if (!responsive) {
      throw new IosDeviceException(this, String.format("Device %s not responsive", udid));
    }
  }

  @Override
  public String toString() {
    return udid;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(udid);
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof FakeIosDevice && udid.equals(((FakeIosDevice) obj).udid);
  }

  /** Fake implementation of {@link RealDevice} */
  public static class FakeRealDevice extends FakeIosDevice implements RealDevice {
    private int batteryLevel = 0;
    private boolean restarting = false;

    public FakeRealDevice() {}

    public FakeRealDevice setBatteryLevel(int batteryLevel) {
      checkArgument(batteryLevel >= 0 && batteryLevel <= 100);
      this.batteryLevel = batteryLevel;
      return this;
    }

    @Override
    public int batteryLevel() throws IosDeviceException {
      super.checkResponsive();
      return batteryLevel;
    }

    @Override
    public void installProfile(Path profilePath) throws IosDeviceException {
      super.checkResponsive();
    }

    @Override
    public void removeProfile(String identifier) throws IosDeviceException {
      super.checkResponsive();
    }

    @Override
    public ImmutableList<ConfigurationProfile> listConfigurationProfiles()
        throws IosDeviceException {
      return ImmutableList.of();
    }

    @Override
    public boolean isRestarting() {
      return restarting;
    }

    public FakeIosDevice setRestarting(boolean restarting) {
      this.restarting = restarting;
      return this;
    }

    @Override
    public void syncToSystemTime() throws IosDeviceException {}
  }

  /** Fake implementation of {@link SimulatorDevice} */
  public static class FakeSimulatorDevice extends FakeIosDevice implements SimulatorDevice {
    public FakeSimulatorDevice() {}

    @Override
    public void shutdown() throws IosDeviceException {
      super.responsive = false;
    }

    @Override
    public void startup() throws IosDeviceException {
      super.responsive = true;
    }

    @Override
    public void erase() throws IosDeviceException {
      super.checkResponsive();
    }
  }
}
