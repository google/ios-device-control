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
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assert_;

import com.google.iosdevicecontrol.command.Command;
import com.google.iosdevicecontrol.command.testing.FakeProcess;
import com.google.iosdevicecontrol.IosDeviceException;
import com.google.iosdevicecontrol.IosDeviceException.Remedy;
import com.google.iosdevicecontrol.testing.FakeIosDevice.FakeRealDevice;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link com.google.iosdevicecontrol.real.RealAppProcess}. */
@RunWith(JUnit4.class)

public class RealAppProcessTest {
  private static final String APPRUNNER_STDERR_PREFIX =
      "sent[19] ($QStartNoAckMode#b0)\nsent[18] ($qLaunchSuccess#00)\nrecv[0] ()\n";
  private static final RealDevice IOS_DEVICE = new FakeRealDevice();
  private static final Command APPRUNNER_COMMAND = command("idevice-app-runner");

  private FakeProcess commandProcess;
  private RealAppProcess appProcess;

  @Before
  public void setup() throws Exception {
    commandProcess = FakeProcess.start(APPRUNNER_COMMAND);
    appProcess = new RealAppProcess(IOS_DEVICE, commandProcess);
    commandProcess.writeStderrUtf8(APPRUNNER_STDERR_PREFIX);
  }

  @Test
  public void testMap4294967295Error() throws Exception {
    testErrorHasRemedy(
        Remedy.REINSTALL_APP, "Error: recv ($E4294967295#00) instead of expected ($OK#00)\n");
  }

  @Test
  public void testMapFailedToGetTaskForProcessError() throws Exception {
    testErrorHasRemedy(
        Remedy.REINSTALL_APP,
        "Error: recv ($Efailed to get the task for process -1#00) instead of expected ($OK#00)\n");
  }

  @Test
  public void testMapNotFoundError() throws Exception {
    testErrorHasRemedy(
        Remedy.REINSTALL_APP, "Error: recv ($ENotFound#00) instead of expected ($OK#00)\n");
  }

  @Test
  public void testMapNoSuchFileOrDirectoryError() throws Exception {
    testErrorHasRemedy(
        Remedy.REINSTALL_APP,
        "Error: recv ($ENo such file or directory (/private/var/mobile/Containers/Bundle/"
            + "Application/46E85E58-864B-4EFA/OpenUrl.app)#00) instead of expected ($OK#00)\n");
  }

  @Test
  public void testMapUnknownAppIdError() throws Exception {
    testErrorHasRemedy(
        Remedy.REINSTALL_APP, "Unknown APPID (com.google.openURL) is not in:");
  }

  @Test
  public void testMapTimedOutTryingToLaunchApp() throws Exception {
    testErrorHasRemedy(
        Remedy.RESTART_DEVICE,
        "Error: recv ($Etimed out trying to launch app#00) instead of expected ($OK#00)\n");
  }

  private void testErrorHasRemedy(Remedy remedy, String stderr) throws Exception {
    commandProcess.writeStderrUtf8(stderr);
    commandProcess.setTerminated(1);
    try {
      appProcess.await();
      assert_().fail();
    } catch (IosDeviceException e) {
      assertThat(e.remedy()).hasValue(remedy);
    }
  }
}
