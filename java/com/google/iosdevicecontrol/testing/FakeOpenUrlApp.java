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

import com.google.iosdevicecontrol.IosAppBundleId;
import com.google.iosdevicecontrol.IosAppInfo;
import com.google.iosdevicecontrol.IosDevice;
import com.google.iosdevicecontrol.IosDeviceException;
import com.google.iosdevicecontrol.openurl.AppResult.CheckWifiResult;
import com.google.iosdevicecontrol.openurl.AppResult.OpenUrlResult;
import com.google.iosdevicecontrol.openurl.AppResult.WifiConnection;
import com.google.iosdevicecontrol.openurl.OpenUrlApp;
import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;

/** A fake OpenUrlApp for testing. */
public class FakeOpenUrlApp extends OpenUrlApp {
  private static final IosAppInfo FAKE_APP_INFO =
      IosAppInfo.builder().bundleId(new IosAppBundleId("fake.google.OpenUrl")).build();

  private Optional<WifiConnection> wifiConnection = Optional.empty();

  public FakeOpenUrlApp() {}

  public FakeOpenUrlApp(WifiConnection wifiConnection) {
    setWifiConnection(wifiConnection);
  }

  @Override
  public IosAppInfo appInfo() {
    return FAKE_APP_INFO;
  }

  @Override
  public Path ipaPath() {
    // TODO(user): Create a FakeIosApp class and return an instance of it here.
    // Once that is done, consider moving this class to the java ios/testing directory.
    throw new UnsupportedOperationException();
  }

  @Override
  public OpenUrlResult openUrl(IosDevice device, URI url) {
    return new OpenUrlResult("fake output", true);
  }

  @Override
  public void openBlankPage(IosDevice device) {}

  @Override
  public CheckWifiResult checkWifi(IosDevice device) throws IosDeviceException {
    return new CheckWifiResult("fake output", wifiConnection);
  }

  public FakeOpenUrlApp setWifiConnection(WifiConnection wifiConnection) {
    this.wifiConnection = Optional.of(wifiConnection);
    return this;
  }

  public FakeOpenUrlApp clearWifiConnection() {
    wifiConnection = Optional.empty();
    return this;
  }
}
