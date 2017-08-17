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

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.base.VerifyException;
import com.google.iosdevicecontrol.util.FluentLogger;
import com.google.common.net.InetAddresses;
import com.google.iosdevicecontrol.IosAppBundleId;
import com.google.iosdevicecontrol.IosAppInfo;
import com.google.iosdevicecontrol.IosAppProcess;
import com.google.iosdevicecontrol.IosDevice;
import com.google.iosdevicecontrol.IosDeviceException;
import com.google.iosdevicecontrol.IosDeviceException.Remedy;
import com.google.iosdevicecontrol.openurl.AppResult.CheckWifiResult;
import com.google.iosdevicecontrol.openurl.AppResult.OpenUrlResult;
import com.google.iosdevicecontrol.openurl.AppResult.WifiConnection;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A java wrapper to openURL app functionality. */
public abstract class OpenUrlApp {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /**
   * Singleton instance of the OpenUrlApp stored as a Java resourced, supplied lazily to avoid
   * extracting the resource unless it is actually used.
   */
  private static final Supplier<OpenUrlApp> RESOURCE_OPEN_URL_APP =
      Suppliers.memoize(ResourceOpenUrlApp::new);

  /** Returns the openURL app instance from the Java resource. */
  public static OpenUrlApp fromResource() {
    return RESOURCE_OPEN_URL_APP.get();
  }

  protected OpenUrlApp() {}

  /** Path to the openURL ipa. */
  public abstract Path ipaPath();

  /** Info about the openURL app. */
  public abstract IosAppInfo appInfo();

  /** Bundle identifier of the openURL app; same as {@code appInfo.bundleId())}. */
  public final IosAppBundleId bundleId() {
    return appInfo().bundleId();
  }

  /**
   * If there is an installed app registered to handle the scheme of the specified URL, opens that
   * URL and returns true; otherwise, returns false.
   *
   * @throws IosDeviceException - if there is an error communicating with the device
   */
  public abstract OpenUrlResult openUrl(IosDevice device, URI url) throws IosDeviceException;

  /**
   * Opens a blank page in Safari.
   *
   * @throws IosDeviceException - if there is an error communicating with the device
   */
  public abstract void openBlankPage(IosDevice device) throws IosDeviceException;

  /**
   * Looks up the the current WiFi connection of the device.
   *
   * @throws IosDeviceException - if there is an error communicating with the device
   */
  public abstract CheckWifiResult checkWifi(IosDevice device) throws IosDeviceException;

  @Override
  public final String toString() {
    return appInfo().toString();
  }

  /** An openURL app pulled from a resource. */
  private static final class ResourceOpenUrlApp extends OpenUrlApp {
    private static final Path IPA_PATH;
    private static final IosAppInfo APP_INFO;

    static {
      try {
        IPA_PATH = Paths.get("/usr/local/share/OpenUrl.ipa");
        APP_INFO = IosAppInfo.readFromPath(IPA_PATH);
      } catch (IOException e) {
        throw new VerifyException(e);
      }
    }

    private static final Pattern OPEN_URL_SUCCESS_PATTERN = Pattern.compile("Opened URL");
    // The openURL app only detects IPv4 addresses currently.
    private static final Pattern CHECK_WIFI_IP_ADDRESS_PATTERN =
        Pattern.compile("WiFi is enabled at (\\d+\\.\\d+\\.\\d+\\.\\d+)");
    private static final Pattern CHECK_WIFI_SSID_PATTERN = Pattern.compile("WiFi SSID is (\\S+)");

    @Override
    public Path ipaPath() {
      return IPA_PATH;
    }

    @Override
    public IosAppInfo appInfo() {
      return APP_INFO;
    }

    @Override
    public OpenUrlResult openUrl(IosDevice device, URI url) throws IosDeviceException {
      return openUrl(device, url.toString());
    }

    @Override
    public void openBlankPage(IosDevice device) throws IosDeviceException {
      // Unlike other browsers, Safari does not recognize "about:" as a URL scheme, but it
      // nevertheless supports about:blank. The undocumented but feasible way to open about:blank
      // with an openURL invocation is to pass it the malformed URL "http://about:blank".
      OpenUrlResult result = openUrl(device, "http://about:blank");
      if (!result.success()) {
        throw new IosDeviceException(device, "Unable to open blank url: " + result.rawOutput());
      }
    }

    private OpenUrlResult openUrl(IosDevice device, String url) throws IosDeviceException {
      String output = runAppRobust(device, Duration.ofSeconds(30), url);
      boolean success = OPEN_URL_SUCCESS_PATTERN.matcher(output).find();
      return new OpenUrlResult(output, success);
    }

    @Override
    public CheckWifiResult checkWifi(IosDevice device) throws IosDeviceException {
      String output = runAppRobust(device, Duration.ofSeconds(15), "--check_wifi");
      Matcher ipMatcher = CHECK_WIFI_IP_ADDRESS_PATTERN.matcher(output);
      Matcher ssidMatcher = CHECK_WIFI_SSID_PATTERN.matcher(output);

      Optional<WifiConnection> connection;
      if (ipMatcher.find() && ssidMatcher.find()) {
        InetAddress ip = InetAddresses.forString(ipMatcher.group(1));
        String ssid = ssidMatcher.group(1);
        connection = Optional.of(new AutoValue_AppResult_WifiConnection(ip, ssid));
      } else {
        connection = Optional.empty();
      }

      return new CheckWifiResult(output, connection);
    }

    private String runAppRobust(IosDevice device, Duration timeout, String... args)
        throws IosDeviceException {
      // Install the openURL app if not already installed.
      if (!device.isApplicationInstalled(bundleId())) {
        device.installApplication(ipaPath());
      }

      // Run the openURL app, reinstalling the app and retrying once if necessary.
      try {
        return runAppOnce(device, timeout, args);
      } catch (IosDeviceException e) {
        if (e.remedy().isPresent()) {
          switch (e.remedy().get()) {
            case REINSTALL_APP:
              logger.atInfo().log(
                  "Reinstalling the openURL app on %s and retrying apprunner", device);
              device.installApplication(ipaPath());
              return runAppOnce(device, timeout, args);
            case RESTART_DEVICE:
              logger.atInfo().log("Restarting device %s and retrying apprunner", device);
              device.restart();
              return runAppOnce(device, timeout, args);
            default:
              // fall through
          }
        }
        throw e;
      }
    }

    private String runAppOnce(IosDevice device, Duration timeout, String... args)
        throws IosDeviceException {
      IosAppProcess appProcess = device.runApplication(bundleId(), args);
      try {
        return appProcess.await(timeout);
      } catch (TimeoutException e) {
        throw new IosDeviceException(device, e, Remedy.DISMISS_DIALOG);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IosDeviceException(device, e);
      } finally {
        appProcess.kill();
      }
    }
  }
}
