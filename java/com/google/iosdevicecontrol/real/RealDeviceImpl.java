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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.dd.plist.NSArray;
import com.dd.plist.NSDictionary;
import com.dd.plist.NSNumber;
import com.dd.plist.NSObject;
import com.dd.plist.NSString;
import com.dd.plist.PropertyListFormatException;
import com.dd.plist.XMLPropertyListParser;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.iosdevicecontrol.util.FluentLogger;
import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import com.google.iosdevicecontrol.command.Command;
import com.google.iosdevicecontrol.command.CommandFailureException;
import com.google.iosdevicecontrol.command.CommandProcess;
import com.google.iosdevicecontrol.command.CommandResult;
import com.google.iosdevicecontrol.IosAppBundleId;
import com.google.iosdevicecontrol.IosAppInfo;
import com.google.iosdevicecontrol.IosAppProcess;
import com.google.iosdevicecontrol.IosDevice;
import com.google.iosdevicecontrol.IosDeviceException;
import com.google.iosdevicecontrol.IosDeviceResource;
import com.google.iosdevicecontrol.IosDeviceSocket;
import com.google.iosdevicecontrol.IosModel;
import com.google.iosdevicecontrol.IosVersion;
import com.google.iosdevicecontrol.real.DevDiskImages.DiskImage;
import com.google.iosdevicecontrol.util.CheckedCallable;
import com.google.iosdevicecontrol.util.CheckedCallables;
import com.google.iosdevicecontrol.util.ForwardingSocket;
import com.google.iosdevicecontrol.util.PlistParser;
import com.google.iosdevicecontrol.util.RetryCallable;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;
import javax.xml.parsers.ParserConfigurationException;
import org.joda.time.Duration;
import org.xml.sax.SAXException;

/**
 * Implementation of {@link IosDevice} backed by the libimobiledevice library.
 *
 * <p>TODO(user): Should these methods be synchronized or the class marked NotThreadSafe?
 */
final class RealDeviceImpl implements RealDevice {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final String udid;
  private final IdeviceCommands idevice;
  private final CfgutilCommands cfgutil;
  private final DevDiskImages devDiskImages;

  // All the ideviceinfo values are constant for the lifetime a device is attached to a host, the
  // only exception being TimeIntervalSince1970, which we don't use or care about.
  private final CheckedCallable<NSDictionary, IosDeviceException> getIdeviceInfo =
      CheckedCallables.memoize(this::callIdeviceInfo);

  private final CheckedCallable<IosModel, IosDeviceException> getModel =
      CheckedCallables.memoize(
          () -> {
            NSDictionary info = getIdeviceInfo.call();
            String identifier = getNSString(info, "ProductType");
            String productName = ID_TO_PRODUCT_NAME.get(identifier);
            checkState(productName != null, "No product name found for %s", identifier);
            return IosModel.builder()
                .architecture(getNSString(info, "CPUArchitecture"))
                .identifier(getNSString(info, "ProductType"))
                .productName(productName)
                .build();
          });

  private final CheckedCallable<IosVersion, IosDeviceException> getVersion =
      CheckedCallables.memoize(
          () -> {
            NSDictionary info = getIdeviceInfo.call();
            return IosVersion.builder()
                .buildVersion(getNSString(info, "BuildVersion"))
                .productVersion(getNSString(info, "ProductVersion"))
                .build();
          });

  private final AtomicBoolean systemLoggerStarted = new AtomicBoolean(false);
  private volatile boolean restarting = false;

  RealDeviceImpl(
      String udid, IdeviceCommands idevice, CfgutilCommands cfgutil, DevDiskImages devDiskImages) {
    this.udid = checkNotNull(udid);
    this.idevice = checkNotNull(idevice);
    this.cfgutil = checkNotNull(cfgutil);
    this.devDiskImages = checkNotNull(devDiskImages);
  }

  @Override
  public String udid() {
    return udid;
  }

  @Override
  public boolean isResponsive() {
    try {
      return !await(idevice.date()).isEmpty();
    } catch (IosDeviceException e) {
      return false;
    }
  }

  @Override
  public boolean isRestarting() {
    return restarting;
  }

  @Override
  public int batteryLevel() throws IosDeviceException {
    String batteryLevel =
        await(idevice.info("-k", "BatteryCurrentCapacity", "-q", "com.apple.mobile.battery"));
    try {
      return Integer.parseInt(batteryLevel.trim());
    } catch (NumberFormatException e) {
      throw new IosDeviceException(this, e);
    }
  }

  @Override
  public IosModel model() throws IosDeviceException {
    return getModel.call();
  }

  @Override
  public IosVersion version() throws IosDeviceException {
    return getVersion.call();
  }

  @Override
  public boolean isApplicationInstalled(IosAppBundleId bundleId) throws IosDeviceException {
    for (IosAppInfo appInfo : listApplications()) {
      if (appInfo.bundleId().equals(bundleId)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public ImmutableSet<IosAppInfo> listApplications() throws IosDeviceException {
    String appArrayXml = await(idevice.installer("-l", "-o", "xml"));
    NSArray appArray = (NSArray) PlistParser.fromXml(appArrayXml);

    ImmutableSet.Builder<IosAppInfo> appInfos = ImmutableSet.builder();
    for (NSObject app : appArray.getArray()) {
      appInfos.add(IosAppInfo.readFromPlistDictionary((NSDictionary) app));
    }
    return appInfos.build();
  }

  @Override
  public void installApplication(Path ipaPath) throws IosDeviceException {
    // TODO(user): Add a check that the app is properly signed for the device, like:
    // https://github.com/dermdaly/listdevs/blob/master/listdevs.sh

    // Get the bundle id of the provided ipa.
    IosAppBundleId bundleId;
    try {
      bundleId = IosAppBundleId.readFromIpa(ipaPath);
    } catch (IOException e) {
      throw new IosDeviceException(this, e);
    }

    // Try to install the app. If the bundle id matches an existing installed app but has a
    // mismatched "application identifier entitlement" string, the install fails unnecessarily.
    // In this case, just uninstsall the existing app and then install again.
    try {
      await(idevice.installer("-i", ipaPath.toString()));
    } catch (IosDeviceException e) {
      if (e.getMessage() != null
          && e.getMessage().contains("MismatchedApplicationIdentifierEntitlement")) {
        await(idevice.installer("-U", bundleId.toString()));
        await(idevice.installer("-i", ipaPath.toString()));
      } else {
        throw e;
      }
    }

    // Check that the application successfully installed. This has the dual benefit of possibly
    // catching a failed installation early, but also the mere act of checking whether it's
    // installed seems to ensure the internal list of applications is up-to-date. Otherwise, the
    // following sequence in quick succession sometimes fails: install->reboot->run.
    if (!isApplicationInstalled(bundleId)) {
      throw new IosDeviceException(this, bundleId + " not in application list after install");
    }
  }

  @Override
  public void uninstallApplication(IosAppBundleId bundleId) throws IosDeviceException {
    if (isApplicationInstalled(bundleId)) {
      await(idevice.installer("-U", bundleId.toString()));
    }
  }

  @Override
  public IosAppProcess runApplication(IosAppBundleId bundleId, String... args)
      throws IosDeviceException {
    ImmutableList<String> apprunnerArgs =
        ImmutableList.<String>builder()
            .add("-d", "-s", bundleId.toString(), "--args")
            .add(args)
            .build();

    // The debugserver service used by idevice-app-runner requires the developer image.
    CommandProcess apprunnerProcess =
        retryWithDeveloperImageMount(
            false /* error message goes to stderr */, () -> startApprunner(apprunnerArgs));
    return new RealAppProcess(this, apprunnerProcess);
  }

  @Override
  public IosDeviceResource startSystemLogger(Path logPath) throws IosDeviceException {
    checkState(!systemLoggerStarted.getAndSet(true), "System logger has already been started.");
    CommandProcess syslog = idevice.syslog(logPath);
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
    await(idevice.crashreport(directory.toString()));
  }

  @Override
  public void clearCrashLogs() throws IosDeviceException {
    try {
      Path tempDir = Files.createTempDirectory("artifacts");
      try {
        idevice.crashreport(tempDir.toString());
      } finally {
        MoreFiles.deleteRecursively(tempDir, RecursiveDeleteOption.ALLOW_INSECURE);
      }
    } catch (IOException e) {
      throw new IosDeviceException(this, e);
    }
  }

  @Override
  public void installProfile(Path profilePath) throws IosDeviceException {
    await(cfgutil.installProfile(profilePath.toString()));
  }

  @Override
  public void removeProfile(String identifier) throws IosDeviceException {
    try {
      await(cfgutil.removeProfile(identifier));
    } catch (IosDeviceException e) {
      // Ignore a command failure due to the profile not being installed
      if (!e.getMessage().contains("cfgutil: warning: no such profile")) {
        throw e;
      }
    }
  }

  @Override
  public ImmutableList<ConfigurationProfile> listConfigurationProfiles() throws IosDeviceException {
    String plistXml = await(cfgutil.get("configurationProfiles"));
    NSDictionary plist = (NSDictionary) PlistParser.fromXml(plistXml);
    String ecid = ((NSArray) plist.get("Devices")).objectAtIndex(0).toString();
    NSDictionary output = (NSDictionary) ((NSDictionary) plist.get("Output")).get(ecid);
    NSArray profileArray = (NSArray) output.get("configurationProfiles");

    ImmutableList.Builder<ConfigurationProfile> profiles = ImmutableList.builder();
    for (int i = 0; i < profileArray.count(); i++) {
      NSDictionary profileDict = (NSDictionary) profileArray.objectAtIndex(i);
      profiles.add(
          ConfigurationProfile.builder()
              .displayName(((NSString) profileDict.get("displayName")).getContent())
              .identifier(((NSString) profileDict.get("identifier")).getContent())
              .version(((NSNumber) profileDict.get("version")).intValue())
              .build());
    }
    return profiles.build();
  }

  /**
   * Retries starting the apprunner until stderr output is seen. When no stderr is produced, the
   * debug server is probably wedged, which requires restarting the device and trying again.
   */
  private CommandProcess startApprunner(ImmutableList<String> apprunnerArgs)
      throws IosDeviceException {
    return RetryCallable.retry(
            () -> {
              CommandProcess process = idevice.apprunner(apprunnerArgs.toArray(new String[] {}));
              waitForStderrOutput(process);
              return process;
            })
        .withMaxAttempts(2)
        .withExceptionHandler(
            exception -> {
              // If the debug server appears wedged, reboot and retry.
              if (exception instanceof DebugServerWedgedException) {
                restart();
              } else {
                throw exception;
              }
            })
        .call();
  }

  /** Retry looking for stderr output until ultimately deciding there is a wedge. */
  private void waitForStderrOutput(CommandProcess process) throws IosDeviceException {
    RetryCallable.retry(
            () -> {
              int stderrBytesAvailable;
              try {
                stderrBytesAvailable = process.stderrStream().available();
              } catch (IOException e) {
                process.kill();
                throw new IosDeviceException(RealDeviceImpl.this, e);
              }
              if (stderrBytesAvailable == 0) {
                throw new DebugServerWedgedException();
              }
            })
        .withMaxAttempts(5)
        .withDelay(Duration.standardSeconds(1))
        .withExceptionHandler(
            exception -> {
              if (!(exception instanceof DebugServerWedgedException)) {
                throw exception;
              }
            })
        .call();
  }

  /** Thrown if debug server appears wedged on the device. */
  private class DebugServerWedgedException extends IosDeviceException {
    private DebugServerWedgedException() {
      super(RealDeviceImpl.this, "No apprunner output. Is LLDB wedged?");
    }
  }

  @Override
  public void syncToSystemTime() throws IosDeviceException {
    // idevicedate does not currently produce a time string that is parseable, because it uses
    // (ambiguous) time zone abbreviations rather than unique time zone offsets or ids. If we
    // modify idevicedate to produce a unique datetime string, consider changing this method to
    // parse and return that date/time.
    await(idevice.date("--sync"));
  }

  @Override
  public byte[] takeScreenshot() throws IosDeviceException {
    try {
      Path screenshotPath = Files.createTempFile("screenshot", ".out");
      try {
        // The screenshot service used by idevicescreenshot requires the developer image.
        CommandProcess screenshotProcess =
            retryWithDeveloperImageMount(
                true /* error message goes to stdout */,
                () -> idevice.screenshot(screenshotPath.toString()));
        await(screenshotProcess);
        ImageInputStream iis = ImageIO.createImageInputStream(screenshotPath.toFile());
        String format = ImageIO.getImageReaders(iis).next().getFormatName();
        // iOS versions < 9 return TIFF images instead of PNG.
        return format.equals("png") ? Files.readAllBytes(screenshotPath) : convertToPng(iis);
      } finally {
        Files.deleteIfExists(screenshotPath);
      }
    } catch (IOException e) {
      throw new IosDeviceException(this, e);
    }
  }

  private byte[] convertToPng(ImageInputStream iis) throws IOException {
    ByteArrayOutputStream pngOut = new ByteArrayOutputStream();
    BufferedImage image = ImageIO.read(iis);
    ImageIO.write(image, "png", pngOut);
    return pngOut.toByteArray();
  }

  private static String getNSString(NSDictionary nsDict, String key) {
    NSObject value = nsDict.get(key);
    checkArgument(value instanceof NSString, "Key %s mapped to a non-string value: %s", key, value);
    return ((NSString) value).getContent();
  }

  /**
   * Mounts the developer image on the device.
   *
   * @throws IllegalStateException - if no developer image is found for the device's iOS version
   */
  private void mountDeveloperImage() throws IosDeviceException {
    DiskImage diskImage = devDiskImages.findForVersion(version().productVersion());
    CommandProcess mountingImage =
        idevice.imagemounter(
            diskImage.imagePath().toString(), diskImage.signaturePath().toString());
    // ideviceimagemounter sometimes returns a 255 exit code even when it succeeds.
    await(mountingImage, 0, 255);
  }

  /** Retries the specified callable, mounting the developer image if it is not mounted. */
  private CommandProcess retryWithDeveloperImageMount(
      boolean errorToStdout, CheckedCallable<CommandProcess, IosDeviceException> callable)
      throws IosDeviceException {
    // Retries the callable until it starts successfully. When real or spurious image mounting
    // errors occur, it attempts to mount the image and tries again. We should in theory be able
    // to
    // use `ideviceimagemounter -l` to check if an image is mounted, but (1) it usually is already
    // mounted so this is faster; and (2) `ideviceimagemounter -l` lies on iOS7
    // (see: https://github.com/libimobiledevice/libimobiledevice/issues/207).
    return RetryCallable.retry(
            () -> {
              CommandProcess process = callable.call();
              BufferedReader output =
                  new BufferedReader(
                      errorToStdout ? process.stdoutReaderUtf8() : process.stderrReaderUtf8());

              // The first output line may indicate the developer image isn't mounted.
              String firstLine;
              try {
                firstLine = output.readLine();
              } catch (IOException e) {
                throw new IosDeviceException(RealDeviceImpl.this, e);
              }
              if (firstLine.startsWith("Could not start")) {
                await(process, 255);
                throw new NoDeveloperImageMountedException();
              }

              return process;
            })
        .withMaxAttempts(10)
        .withDelay(Duration.standardSeconds(3))
        .withExceptionHandler(
            exception -> {
              // If the  developer image didn't appear mounted, mount and retry.
              if (exception instanceof NoDeveloperImageMountedException) {
                logger.atInfo().log(
                    "Mounting developer image on %s and retrying", RealDeviceImpl.this);
                mountDeveloperImage();
              } else {
                throw exception;
              }
            })
        .call();
  }

  /** Thrown if the developer image doesn't appear mounted when running an app. */
  private class NoDeveloperImageMountedException extends IosDeviceException {
    private NoDeveloperImageMountedException() {
      super(RealDeviceImpl.this, "Cannot run apps without mounting a developer image");
    }
  }

  @SuppressWarnings("Finally") // b/64321948
  @Override
  public IosDeviceSocket openWebInspectorSocket() throws IosDeviceException {
    int inspectorPort;
    try (ServerSocket socket = new ServerSocket(0)) {
      inspectorPort = socket.getLocalPort();
    } catch (IOException e) {
      throw new IosDeviceException(this, e);
    }

    CommandProcess inspectorProcess = idevice.webinspectorproxy(Integer.toString(inspectorPort));
    Closeable closeProcess =
        () -> {
          try {
            inspectorProcess.kill().await();
          } catch (CommandFailureException e) {
            throw new IOException(e);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
          }
        };

    // Socket may not be open right away, so we retry.
    Socket socket;
    try {
      socket =
          RetryCallable.retry(() -> new Socket("localhost", inspectorPort))
              .withDelay(Duration.standardSeconds(1))
              .withMaxAttempts(15)
              .call();
    } catch (IOException e) {
      IosDeviceException deviceEx = new IosDeviceException(this, e);
      try {
        closeProcess.close();
      } catch (Throwable suppressed) {
        deviceEx.addSuppressed(suppressed);
      } finally {
        throw deviceEx;
      }
    }

    // Wrap the socket with an augmented close method that also closes the process.
    return IosDeviceSocket.wrap(
        this,
        new ForwardingSocket(socket) {
          @Override
          public synchronized void close() throws IOException {
            try {
              closeProcess.close();
            } finally {
              super.close();
            }
          }
        });
  }

  @Override
  public void restart() throws IosDeviceException {
    await(idevice.diagnostics("restart"));

    restarting = true;
    try {
      // Device should take at least 30 seconds to reboot.
      sleep(Duration.standardSeconds(30));
      // Now check periodically for the device to become responsive again.
      retryResponsiveAfterReboot.call();
    } finally {
      restarting = false;
    }
  }

  private final RetryCallable<Void, IosDeviceException> retryResponsiveAfterReboot =
      RetryCallable.retry(
              () -> {
                if (!isResponsive()) {
                  throw new IosDeviceException(
                      RealDeviceImpl.this, "Device unresponsive after reboot");
                }
              })
          .withMaxAttempts(12)
          .withDelay(Duration.standardSeconds(5));

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(IosDevice.class).add("udid", udid).toString();
  }

  private NSDictionary callIdeviceInfo() throws IosDeviceException {
    String infoText = await(idevice.info("-x"));
    byte[] infoBytes = infoText.getBytes(StandardCharsets.UTF_8);
    try {
      return (NSDictionary) XMLPropertyListParser.parse(infoBytes);
    } catch (ParserConfigurationException
        | ParseException
        | PropertyListFormatException
        | IOException
        | SAXException e) {
      throw new IosDeviceException(RealDeviceImpl.this, e);
    }
  }

  /**
   * Waits for a process to terminate and returns the result of the execution. Optionally specify
   * expected process exit codes, which if not specified, is assumed to be only zero. If the exit
   * code is not one of the expected values, this method throws an IosDeviceException.
   */
  private String await(CommandProcess process, Integer... expectedExitCodes)
      throws IosDeviceException {
    CommandResult result = awaitResult(process);

    // If an idevice command fails because a device is not trusted, automatically trust and retry.
    // Cfgutil commands do not report "lockdownd" errors, but just in case we check that the
    // executable does start with "idevice," to avoid any chance of an infinite recursion.
    if (result.exitCode() != 0) {
      Command command = process.command();
      if (Paths.get(command.executable()).getFileName().toString().startsWith("idevice")
          && result.stderrStringUtf8().contains("Could not connect to lockdownd")) {
        if (cfgutil.isSupervised()) {
          await(cfgutil.pair());
          result = awaitResult(IdeviceCommands.exec(command));
        } else {
          logger.atWarning().log(
              "set a configuration profile in the device host to automatically pair the device");
        }
      }
    }

    Set<Integer> expectedExitCodeSet =
        expectedExitCodes.length == 0 ? ImmutableSet.of(0) : ImmutableSet.copyOf(expectedExitCodes);
    if (!expectedExitCodeSet.contains(result.exitCode())) {
      throw new IosDeviceException(this, "Unexpected exit code in result: " + result);
    }
    return result.stdoutStringUtf8();
  }

  private CommandResult awaitResult(CommandProcess process) throws IosDeviceException {
    try {
      return process.await();
    } catch (CommandFailureException e) {
      return e.result();
    } catch (InterruptedException e) {
      throw propagateInterrupt(e);
    }
  }

  /**
   * Sleeps for the specified duration, throwing an IosDeviceException and re-interrupts the thread
   * if an InterruptedException is thrown.
   */
  private void sleep(Duration duration) throws IosDeviceException {
    try {
      Thread.sleep(duration.getMillis());
    } catch (InterruptedException e) {
      throw propagateInterrupt(e);
    }
  }

  /** Propagates an interrupted exception as an IosDeviceException and re-interrupts the thread. */
  private IosDeviceException propagateInterrupt(InterruptedException e) throws IosDeviceException {
    Thread.currentThread().interrupt();
    throw new IosDeviceException(this, e);
  }

  // There is no deterministic way to know the product name of a model without enumerating them.
  private static final ImmutableMap<String, String> ID_TO_PRODUCT_NAME;

  static {
    ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
    addModels(builder, "iPad", "iPad1,1");
    addModels(builder, "iPad 2", "iPad2,1", "iPad2,2", "iPad2,3", "iPad2,4");
    addModels(builder, "iPad 3", "iPad3,1", "iPad3,2", "iPad3,3");
    addModels(builder, "iPad 4", "iPad3,4", "iPad3,5", "iPad3,6");
    addModels(builder, "iPad 5", "iPad6,11", "iPad6,12");
    addModels(builder, "iPad Air", "iPad4,1", "iPad4,2", "iPad4,3");
    addModels(builder, "iPad Air 2", "iPad5,3", "iPad5,4");
    addModels(builder, "iPad mini", "iPad2,5", "iPad2,6", "iPad2,7");
    addModels(builder, "iPad mini 2", "iPad4,4", "iPad4,5", "iPad4,6");
    addModels(builder, "iPad mini 3", "iPad4,7", "iPad4,8", "iPad4,9");
    addModels(builder, "iPad mini 4", "iPad5,1", "iPad5,2");
    addModels(builder, "iPad Pro (9.7-inch)", "iPad6,3", "iPad6,4");
    addModels(builder, "iPad Pro (10.5-inch)", "iPad7,3", "iPad7,4");
    addModels(builder, "iPad Pro (12.9-inch)", "iPad6,7", "iPad6,8");
    addModels(builder, "iPad Pro (12.9-inch) 2", "iPad7,1", "iPad7,2");
    addModels(builder, "iPhone", "iPhone1,1");
    addModels(builder, "iPhone 3G", "iPhone1,2");
    addModels(builder, "iPhone 3GS", "iPhone2,1");
    addModels(builder, "iPhone 4", "iPhone3,1", "iPhone3,2", "iPhone3,3");
    addModels(builder, "iPhone 4S", "iPhone4,1");
    addModels(builder, "iPhone 5", "iPhone5,1", "iPhone5,2");
    addModels(builder, "iPhone 5c", "iPhone5,3", "iPhone5,4");
    addModels(builder, "iPhone 5s", "iPhone6,1", "iPhone6,2");
    addModels(builder, "iPhone 6", "iPhone7,2");
    addModels(builder, "iPhone 6 Plus", "iPhone7,1");
    addModels(builder, "iPhone 6s", "iPhone8,1");
    addModels(builder, "iPhone 6s Plus", "iPhone8,2");
    addModels(builder, "iPhone SE", "iPhone8,4");
    addModels(builder, "iPhone 7", "iPhone9,1", "iPhone9,3");
    addModels(builder, "iPhone 7 Plus", "iPhone9,2", "iPhone9,4");
    ID_TO_PRODUCT_NAME = builder.build();
  }

  private static void addModels(
      ImmutableMap.Builder<String, String> builder, String productName, String... ids) {
    for (String id : ids) {
      builder.put(id, productName);
    }
  }
}
