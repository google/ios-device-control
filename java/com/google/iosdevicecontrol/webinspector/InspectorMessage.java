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
import com.dd.plist.NSString;

/** A message sent to or from the web inspector in the webkit remote debug protocol. */
public abstract class InspectorMessage extends MessageDict {
  private static final String SELECTOR_KEY = "__selector";
  private static final String ARGUMENT_KEY = "__argument";

  /** Converts the given plist to an inspector message. */
  public static InspectorMessage fromPlist(NSDictionary plist) {
    try {
      NSString selectorString = (NSString) plist.get(SELECTOR_KEY);
      NSDictionary argumentDict = (NSDictionary) plist.get(ARGUMENT_KEY);
      MessageSelector selector = MessageSelector.forString(selectorString.getContent());
      Builder messageBuilder = selector.newMessageBuilder();
      messageBuilder.populateFromPlistDict(argumentDict);
      return messageBuilder.build();
    } catch (RuntimeException e) {
      throw new IllegalArgumentException("Invalid plist: " + plist.toXMLPropertyList(), e);
    }
  }

  /** Converts this inspector message as a plist. */
  public final NSDictionary toPlist() {
    NSDictionary plist = new NSDictionary();
    plist.put(SELECTOR_KEY, selector().toString());
    plist.put(ARGUMENT_KEY, toPlistDict());
    return plist;
  }

  /** Returns the selector (the type of the message). */
  public abstract MessageSelector selector();

  /** Common supertype for all inspector message builders. */
  public abstract static class Builder extends MessageDict.Builder {
    @Override
    public abstract InspectorMessage build();
  }
}
