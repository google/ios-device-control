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

package com.google.iosdevicecontrol.command;

import static com.google.iosdevicecontrol.command.Command.command;

import java.io.BufferedReader;
import java.nio.file.Path;

/**
 * This class contains several examples used by README.md, to ensure that they compile and stay
 * up-to-date. It should not be referenced by any other code. This class is part of :command so that
 * build issues with the examples will be caught immediately.
 *
 * @deprecated No one should be using this class.
 */
@Deprecated
class Examples {
  void helloWorld() throws Exception {
    Command.command("echo", "Hello", "World").execute();
  }

  void stdoutStderr(Path executable) throws Exception {
    CommandResult result = command(executable).execute();
    System.out.println(result.stdoutStringUtf8());
    System.out.println(result.stderrStringUtf8());
  }

  void template() throws Exception {
    Command template = command("echo", "Foo");
    template.withArgumentsAppended("Bar").execute(); // echos "Foo Bar"
    template.withArgumentsAppended("Baz").execute(); // echos "Foo Baz"
    template.withArguments("Fizz", "Buzz").execute(); // echos "Fizz Buzz"
  }

  void running(Path executable) throws Exception {
    CommandProcess process = command(executable).start();
    while (process.isAlive()) {
      System.out.println("Still running...");
      Thread.sleep(100);
    }
    CommandResult result = process.await(); // process is done now
    System.out.println(result.exitCode());
  }

  void failure(Path executableThatMightFail) throws Exception {
    try {
      CommandResult result = command(executableThatMightFail).execute();
      System.out.println(result.stdoutStringUtf8());
    } catch (CommandFailureException e) {
      System.out.println(e.result().stderrStringUtf8());
    }
  }

  void customSuccess(Path executableThatShouldFail) throws Exception {
    CommandResult result = command(executableThatShouldFail)
        .withSuccessExitCodes(1, 2, 10) // Note that 0 is not considered a "successful" exit now
        .execute();
    System.out.println(result.exitCode());
  }

  void readWhileRunning(Path longRunningCommand) throws Exception {
    CommandProcess process = command(longRunningCommand).start();
    try (BufferedReader stdout = new BufferedReader(process.stdoutReaderUtf8())) {
      String line;
      while ((line = stdout.readLine()) != null) {
        System.out.println(line);
      }
    }
    // this should return immediately since the process has closed stdout
    CommandResult result = process.await();
    // The full output is still available in the result
    System.out.println("stdout: " + result.stdoutStringUtf8());
    System.out.println("stderr: " + result.stderrStringUtf8());
  }
}
