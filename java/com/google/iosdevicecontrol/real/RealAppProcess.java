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
import static com.google.iosdevicecontrol.IosDeviceException.Remedy.REINSTALL_APP;
import static com.google.iosdevicecontrol.IosDeviceException.Remedy.RESTART_DEVICE;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import com.google.common.collect.ImmutableMap;
import com.google.iosdevicecontrol.command.CommandFailureException;
import com.google.iosdevicecontrol.command.CommandProcess;
import com.google.iosdevicecontrol.IosAppProcess;
import com.google.iosdevicecontrol.IosDevice;
import com.google.iosdevicecontrol.IosDeviceException;
import com.google.iosdevicecontrol.IosDeviceException.Remedy;
import java.io.Reader;
import java.time.Duration;
import java.util.Map.Entry;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

/** Implementation of {@link IosAppProcess} backed by an idevice-app-runner command process. */
final class RealAppProcess implements IosAppProcess {
  private static final ImmutableMap<Pattern, Remedy> LAST_LINE_PATTERN_TO_REMEDY;

  static {
    ImmutableMap.Builder<Pattern, Remedy> pattern2Remedy = ImmutableMap.builder();
    // http://stackoverflow.com/questions/26287365
    pattern2Remedy.put(Pattern.compile("\\$E4294967295#"), REINSTALL_APP);
    // https://developer.apple.com/library/ios/qa/qa1682/_index.html
    pattern2Remedy.put(Pattern.compile("\\$Efailed to get the task for process.*#"), REINSTALL_APP);
    // http://stackoverflow.com/questions/10167442
    pattern2Remedy.put(Pattern.compile("\\$ENo such file or directory.*#"), REINSTALL_APP);
    // http://stackoverflow.com/questions/10833151
    pattern2Remedy.put(Pattern.compile("\\$ENotFound#"), REINSTALL_APP);
    // http://stackoverflow.com/questions/26032085
    pattern2Remedy.put(Pattern.compile("\\$Etimed out trying to launch app#"), RESTART_DEVICE);
    pattern2Remedy.put(Pattern.compile("Unknown APPID"), REINSTALL_APP);
    LAST_LINE_PATTERN_TO_REMEDY = pattern2Remedy.build();
  }

  private final IosDevice device;
  private final CommandProcess process;

  RealAppProcess(IosDevice device, CommandProcess process) {
    this.device = checkNotNull(device);
    this.process = checkNotNull(process);
  }

  @Override
  public RealAppProcess kill() {
    process.kill();
    return this;
  }

  @Override
  public String await() throws IosDeviceException, InterruptedException {
    try {
      return process.await().stdoutStringUtf8();
    } catch (CommandFailureException e) {
      throw mapCommandFailureException(e);
    }
  }

  @Override
  public String await(Duration timeout)
      throws IosDeviceException, TimeoutException, InterruptedException {
    try {
      return process.await(timeout.toNanos(), NANOSECONDS).stdoutStringUtf8();
    } catch (CommandFailureException e) {
      throw mapCommandFailureException(e);
    }
  }

  @Override
  public Reader outputReader() {
    return process.stdoutReaderUtf8();
  }

  private IosDeviceException mapCommandFailureException(CommandFailureException e) {
    String stderr = e.result().stderrStringUtf8().trim();
    int lastLineIndex = stderr.lastIndexOf('\n');
    String lastLine = lastLineIndex < 0 ? stderr : stderr.substring(lastLineIndex);
    // See if there's a suggested remedy for this error.
    for (Entry<Pattern, Remedy> entry : LAST_LINE_PATTERN_TO_REMEDY.entrySet()) {
      if (entry.getKey().matcher(lastLine).find()) {
        return new IosDeviceException(device, e, entry.getValue());
      }
    }

    return new IosDeviceException(device, e);
  }
}
