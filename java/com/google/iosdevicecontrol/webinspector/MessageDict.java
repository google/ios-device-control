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
import com.dd.plist.NSObject;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Map.Entry;
import javax.json.JsonObject;

/**
 * Common supertype for message components that map {@link MessageKey}s to values. We assume that
 * the type of a key's value (String, int, etc) will be the same across all such dictionaries.
 */
abstract class MessageDict {
  /** Converts this message dictionary to a plist dictionary. */
  final NSDictionary toPlistDict() {
    NSDictionary dict = new NSDictionary();
    for (MessageKey key : MessageKey.values()) {
      try {
        dict.put(key.toString(), key.getValueAsPlistObject(this));
      } catch (UndefinedPropertyException ignored) {
        // If the property is undefined, intentionally don't add it.
      }
    }
    return dict;
  }

  // The getters and builder setters throw UndefinedPropertyException in this class.
  // Subtypes choose override and implement the properties that are defined on them.

  /** Returns the value of {@link MessageKey#APPLICATION_BUNDLE_IDENTIFIER}. */
  String applicationBundleId() {
    throw new UndefinedPropertyException();
  }

  /** Returns the value of {@link MessageKey#APPLICATION_DICTIONARY}. */
  ImmutableList<InspectorApplication> applicationDictionary() {
    throw new UndefinedPropertyException();
  }

  /** Returns the value of {@link MessageKey#APPLICATION_IDENTIFIER}. */
  String applicationId() {
    throw new UndefinedPropertyException();
  }

  /** Returns the value of {@link MessageKey#APPLICATION_NAME}. */
  String applicationName() {
    throw new UndefinedPropertyException();
  }

  /** Returns the value of {@link MessageKey#AUTOMATICALLY_PAUSE}. */
  boolean automaticallyPause() {
    throw new UndefinedPropertyException();
  }

  /** Returns the value of {@link MessageKey#CONNECTION_IDENTIFIER}. */
  String connectionId() {
    throw new UndefinedPropertyException();
  }

  /** Returns the value of {@link MessageKey#DESTINATION}. */
  String destination() {
    throw new UndefinedPropertyException();
  }

  /** Returns the value of {@link MessageKey#DRIVER_DICTIONARY}. */
  ImmutableList<InspectorDriver> driverDictionary() {
    throw new UndefinedPropertyException();
  }

  /** Returns the value of {@link MessageKey#HOST_APPLICATION_IDENTIFIER}. */
  String hostApplicationId() {
    throw new UndefinedPropertyException();
  }

  /** Returns the value of {@link MessageKey#IS_APPLICATION_ACTIVE}. */
  boolean isApplicationActive() {
    throw new UndefinedPropertyException();
  }

  /** Returns the value of {@link MessageKey#IS_APPLICATION_PROXY}. */
  boolean isApplicationProxy() {
    throw new UndefinedPropertyException();
  }

  /** Returns the value of {@link MessageKey#IS_APPLICATION_READY}. */
  boolean isApplicationReady() {
    throw new UndefinedPropertyException();
  }

  /** Returns the value of {@link MessageKey#LISTING}. */
  ImmutableList<InspectorPage> listing() {
    throw new UndefinedPropertyException();
  }

  /** Returns the value of {@link MessageKey#MESSAGE_DATA}. */
  JsonObject messageData() {
    throw new UndefinedPropertyException();
  }

  /** Returns the value of {@link MessageKey#PAGE_IDENTIFIER}. */
  int pageId() {
    throw new UndefinedPropertyException();
  }

  /** Returns the value of {@link MessageKey#REMOTE_AUTOMATION_ENABLED}. */
  boolean remoteAutomationEnabled() {
    throw new UndefinedPropertyException();
  }

  /** Returns the value of {@link MessageKey#SENDER}. */
  String sender() {
    throw new UndefinedPropertyException();
  }

  /** Returns the value of {@link MessageKey#SIMULATOR_BUILD}. */
  String simulatorBuild() {
    throw new UndefinedPropertyException();
  }

  /** Returns the value of {@link MessageKey#SIMULATOR_NAME}. */
  String simulatorName() {
    throw new UndefinedPropertyException();
  }

  /** Returns the value of {@link MessageKey#SIMULATOR_PRODUCT_VERSION}. */
  String simulatorProductVersion() {
    throw new UndefinedPropertyException();
  }

  /** Returns the value of {@link MessageKey#SOCKET_DATA}. */
  JsonObject socketData() {
    throw new UndefinedPropertyException();
  }

  /** Returns the value of {@link MessageKey#TITLE}. */
  String title() {
    throw new UndefinedPropertyException();
  }

  /** Returns the value of {@link MessageKey#TYPE}. */
  String type() {
    throw new UndefinedPropertyException();
  }

  /** Returns the value of {@link MessageKey#URL}. */
  String url() {
    throw new UndefinedPropertyException();
  }

  static <T> T fromOptional(Optional<T> property) {
    if (property.isPresent()) {
      return property.get();
    } else {
      throw new UndefinedPropertyException();
    }
  }

  /** Common supertype for all {@link MessageDict} builders. */
  @SuppressWarnings("unused")
  abstract static class Builder {
    /** Populates this message dictionary builder from the a plist dictionary. */
    final void populateFromPlistDict(NSDictionary dict) {
      for (Entry<String, NSObject> e : dict.entrySet()) {
        MessageKey key = MessageKey.forString(e.getKey());
        key.setValueFromPlistObject(this, e.getValue());
      }
    }

    /** Sets the value of {@link MessageKey#APPLICATION_BUNDLE_IDENTIFIER}. */
    Builder applicationBundleId(String applicationBundleId) {
      throw new UndefinedPropertyException();
    }

    /** Sets the value of {@link MessageKey#APPLICATION_DICTIONARY}. */
    Builder applicationDictionary(List<InspectorApplication> applicationDictionary) {
      throw new UndefinedPropertyException();
    }

    /** Sets the value of {@link MessageKey#APPLICATION_IDENTIFIER}. */
    Builder applicationId(String applicationId) {
      throw new UndefinedPropertyException();
    }

    /** Sets the value of {@link MessageKey#APPLICATION_NAME}. */
    Builder applicationName(String applicationName) {
      throw new UndefinedPropertyException();
    }

    /** Sets the value of {@link MessageKey#AUTOMATICALLY_PAUSE}. */
    Builder automaticallyPause(boolean automaticallyPause) {
      throw new UndefinedPropertyException();
    }

    /** Sets the value of {@link MessageKey#CONNECTION_IDENTIFIER}. */
    Builder connectionId(String applicationName) {
      throw new UndefinedPropertyException();
    }

    /** Sets the value of {@link MessageKey#DESTINATION}. */
    Builder destination(String destination) {
      throw new UndefinedPropertyException();
    }

    /** Sets the value of {@link MessageKey#DRIVER_DICTIONARY}. */
    Builder driverDictionary(List<InspectorDriver> driverDictionary) {
      throw new UndefinedPropertyException();
    }

    /** Sets the value of {@link MessageKey#HOST_APPLICATION_IDENTIFIER}. */
    Builder hostApplicationId(String destination) {
      throw new UndefinedPropertyException();
    }

    /** Sets the value of {@link MessageKey#IS_APPLICATION_ACTIVE}. */
    Builder isApplicationActive(boolean isApplicationActive) {
      throw new UndefinedPropertyException();
    }

    /** Sets the value of {@link MessageKey#IS_APPLICATION_PROXY}. */
    Builder isApplicationProxy(boolean isApplicationProxy) {
      throw new UndefinedPropertyException();
    }

    /** Sets the value of {@link MessageKey#IS_APPLICATION_READY}. */
    Builder isApplicationReady(boolean isApplicationReady) {
      throw new UndefinedPropertyException();
    }

    /** Sets the value of {@link MessageKey#LISTING}. */
    Builder listing(List<InspectorPage> listing) {
      throw new UndefinedPropertyException();
    }

    /** Sets the value of {@link MessageKey#MESSAGE_DATA}. */
    Builder messageData(JsonObject messageData) {
      throw new UndefinedPropertyException();
    }

    /** Sets the value of {@link MessageKey#PAGE_IDENTIFIER}. */
    Builder pageId(int pageId) {
      throw new UndefinedPropertyException();
    }

    /** Sets the value of {@link MessageKey#REMOTE_AUTOMATION_ENABLED}. */
    Builder remoteAutomationEnabled(boolean remoteAutomationEnabled) {
      throw new UndefinedPropertyException();
    }

    /** Sets the value of {@link MessageKey#SENDER}. */
    Builder sender(String sender) {
      throw new UndefinedPropertyException();
    }

    /** Sets the value of {@link MessageKey#SIMULATOR_BUILD}. */
    Builder simulatorBuild(String simulatorBuild) {
      throw new UndefinedPropertyException();
    }

    /** Sets the value of {@link MessageKey#SIMULATOR_NAME}. */
    Builder simulatorName(String simulatorName) {
      throw new UndefinedPropertyException();
    }

    /** Sets the value of {@link MessageKey#SIMULATOR_PRODUCT_VERSION}. */
    Builder simulatorProductVersion(String simulatorProductVersion) {
      throw new UndefinedPropertyException();
    }

    /** Sets the value of {@link MessageKey#SOCKET_DATA}. */
    Builder socketData(JsonObject socketData) {
      throw new UndefinedPropertyException();
    }

    /** Sets the value of {@link MessageKey#TITLE}. */
    Builder title(String title) {
      throw new UndefinedPropertyException();
    }

    /** Sets the value of {@link MessageKey#TYPE}. */
    Builder type(String type) {
      throw new UndefinedPropertyException();
    }

    /** Sets the value of {@link MessageKey#URL}. */
    Builder url(String url) {
      throw new UndefinedPropertyException();
    }

    abstract MessageDict build();
  }

  /** Thrown when a property is undefined on this dictionary. */
  private static class UndefinedPropertyException extends RuntimeException {}
}
