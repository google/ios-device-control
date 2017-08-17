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

package com.google.iosdevicecontrol;

import java.io.Reader;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

/** A running iOS application process. */
public interface IosAppProcess {
  /**
   * Kills the process and returns immediately. To kill and then wait for the process to terminate,
   * use kill().await().
   */
  IosAppProcess kill();

  /** Waits for the app to terminate and returns its output. */
  String await() throws IosDeviceException, InterruptedException;

  /** Waits for the app to terminate up to the specified timeout and returns its output. */
  String await(Duration timeout) throws IosDeviceException, InterruptedException, TimeoutException;

  /** Returns a new reader to the UTF-8 encoded streamed output. */
  Reader outputReader();
}
