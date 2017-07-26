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

package com.google.iosdevicecontrol.webinspector;

import com.dd.plist.NSDictionary;
import java.io.Closeable;
import java.io.IOException;
import java.util.Optional;

/** A nexus for communication with a web inspector. */
public interface InspectorSocket extends Closeable {
  /**
   * Sends a plist to the web inspector.
   *
   * @throws IOException if an I/O error occurs.
   */
  void sendMessage(NSDictionary message) throws IOException;

  /**
   * Blocks until a plist is received from the web inspector, or returns empty on EOF.
   *
   * @throws IOException if an I/O error occurs.
   */
  Optional<NSDictionary> receiveMessage() throws IOException;
}
