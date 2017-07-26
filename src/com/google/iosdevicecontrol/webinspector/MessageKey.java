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

import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_UNDERSCORE;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.dd.plist.NSData;
import com.dd.plist.NSDictionary;
import com.dd.plist.NSNumber;
import com.dd.plist.NSObject;
import com.dd.plist.NSString;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import com.google.iosdevicecontrol.util.JavaxJson;
import com.google.iosdevicecontrol.util.StringEnumMap;
import com.google.iosdevicecontrol.webinspector.MessageDict.Builder;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.json.JsonObject;

/**
 * A key in a web inspector protocol message, as listed in:
 * https://github.com/WebKit/webkit/blob/master/Source/JavaScriptCore/inspector/remote/RemoteInspectorConstants.h
 */
enum MessageKey {
  APPLICATION_BUNDLE_IDENTIFIER(
      stringConverter(MessageDict::applicationBundleId, MessageDict.Builder::applicationBundleId)),
  APPLICATION_DICTIONARY(
      dictionaryConverter(
          InspectorApplication::applicationId,
          InspectorApplication::builder,
          MessageDict::applicationDictionary,
          MessageDict.Builder::applicationDictionary)),
  APPLICATION_IDENTIFIER(
      stringConverter(MessageDict::applicationId, MessageDict.Builder::applicationId)),
  APPLICATION_NAME(
      stringConverter(MessageDict::applicationName, MessageDict.Builder::applicationName)),
  AUTOMATICALLY_PAUSE(
      booleanConverter(MessageDict::automaticallyPause, MessageDict.Builder::automaticallyPause),
      "AutomaticallyPause"),
  CONNECTION_IDENTIFIER(
      stringConverter(MessageDict::connectionId, MessageDict.Builder::connectionId)),
  DESTINATION(stringConverter(MessageDict::destination, MessageDict.Builder::destination)),
  DRIVER_DICTIONARY(
      dictionaryConverter(
          InspectorDriver::driverId,
          InspectorDriver::builder,
          MessageDict::driverDictionary,
          MessageDict.Builder::driverDictionary)),
  HOST_APPLICATION_IDENTIFIER(
      stringConverter(MessageDict::hostApplicationId, MessageDict.Builder::hostApplicationId)),
  // WIRIsApplicationActiveKey value is a boolean property that is stored as an integer.
  IS_APPLICATION_ACTIVE(
      integerConverter(
          dict -> dict.isApplicationActive() ? 1 : 0,
          (dict, value) -> dict.isApplicationActive(value == 1))),
  IS_APPLICATION_PROXY(
      booleanConverter(MessageDict::isApplicationProxy, MessageDict.Builder::isApplicationProxy)),
  IS_APPLICATION_READY(
      booleanConverter(MessageDict::isApplicationReady, MessageDict.Builder::isApplicationReady)),
  LISTING(
      dictionaryConverter(
          page -> Integer.toString(page.pageId()),
          InspectorPage::builder,
          MessageDict::listing,
          MessageDict.Builder::listing)),
  MESSAGE_DATA(jsonConverter(MessageDict::messageData, MessageDict.Builder::messageData)),
  PAGE_IDENTIFIER(integerConverter(MessageDict::pageId, MessageDict.Builder::pageId)),
  REMOTE_AUTOMATION_ENABLED(
      booleanConverter(
          MessageDict::remoteAutomationEnabled, MessageDict.Builder::remoteAutomationEnabled)),
  SENDER(stringConverter(MessageDict::sender, MessageDict.Builder::sender)),
  SIMULATOR_BUILD(
      stringConverter(MessageDict::simulatorBuild, MessageDict.Builder::simulatorBuild)),
  SIMULATOR_NAME(stringConverter(MessageDict::simulatorName, MessageDict.Builder::simulatorName)),
  SIMULATOR_PRODUCT_VERSION(
      stringConverter(
          MessageDict::simulatorProductVersion, MessageDict.Builder::simulatorProductVersion)),
  SOCKET_DATA(jsonConverter(MessageDict::socketData, MessageDict.Builder::socketData)),
  TITLE(stringConverter(MessageDict::title, MessageDict.Builder::title)),
  TYPE(stringConverter(MessageDict::type, MessageDict.Builder::type)),
  URL(stringConverter(MessageDict::url, MessageDict.Builder::url), "URLKey");

  private static final StringEnumMap<MessageKey> STRING_TO_KEY =
      new StringEnumMap<>(MessageKey.class);

  /**
   * Returns the <code>MessageKey</code> for the specified string, e.g. returns <code>
   * APPLICATION_IDENTIFIER</code> for the string "WIRApplicationIdentifierKey".
   */
  static MessageKey forString(String s) {
    return STRING_TO_KEY.get(s);
  }

  private final String string;
  private final PlistConverter plistConverter;

  private MessageKey(PlistConverter plistConverter) {
    // E.g. "APPLICATION_IDENTIFIER" becomes "WIRApplicationIdentifierKey"
    string = "WIR" + UPPER_UNDERSCORE.to(UPPER_CAMEL, name()) + "Key";
    this.plistConverter = plistConverter;
  }

  private MessageKey(PlistConverter plistConverter, String wirSuffix) {
    string = "WIR" + wirSuffix;
    this.plistConverter = plistConverter;
  }

  /**
   * Returns the key's value in the specified message dictionary as a plist object, or absent if the
   * property is not present.
   */
  NSObject getValueAsPlistObject(MessageDict dict) {
    return plistConverter.getValueAsPlistObject(dict);
  }

  /** Sets the key's value in the specified message dictionary from a plist object. */
  void setValueFromPlistObject(MessageDict.Builder dict, NSObject plistValue) {
    plistConverter.setValueFromPlistObject(dict, plistValue);
  }

  @Override
  public String toString() {
    return string;
  }

  // Utilities to convert a key's value to and from a plist object.

  @Immutable
  private interface PlistConverter {
    NSObject getValueAsPlistObject(MessageDict dict);

    void setValueFromPlistObject(MessageDict.Builder dict, NSObject value);
  }

  private static PlistConverter booleanConverter(
      Function<MessageDict, Boolean> getter, BiConsumer<MessageDict.Builder, Boolean> setter) {
    return new PlistConverter() {
      @Override
      public NSObject getValueAsPlistObject(MessageDict dict) {
        return new NSNumber(getter.apply(dict));
      }

      @Override
      public void setValueFromPlistObject(Builder dict, NSObject plistObject) {
        setter.accept(dict, ((NSNumber) plistObject).boolValue());
      }
    };
  }

  private static <D extends MessageDict> PlistConverter dictionaryConverter(
      Function<D, String> getId,
      Supplier<MessageDict.Builder> createBuilder,
      Function<MessageDict, ImmutableList<D>> getter,
      BiConsumer<MessageDict.Builder, ImmutableList<D>> setter) {
    return new PlistConverter() {
      @Override
      public NSObject getValueAsPlistObject(MessageDict dict) {
        NSDictionary plistDict = new NSDictionary();
        for (D messageDict : getter.apply(dict)) {
          plistDict.put(getId.apply(messageDict), messageDict.toPlistDict());
        }
        return plistDict;
      }

      @SuppressWarnings("unchecked")
      @Override
      public void setValueFromPlistObject(MessageDict.Builder dict, NSObject plistObject) {
        ImmutableList.Builder<D> messageDicts = ImmutableList.builder();
        for (NSObject plistDict : ((NSDictionary) plistObject).values()) {
          MessageDict.Builder messageDict = createBuilder.get();
          messageDict.populateFromPlistDict((NSDictionary) plistDict);
          messageDicts.add((D) messageDict.build());
        }
        setter.accept(dict, messageDicts.build());
      }
    };
  }

  private static PlistConverter integerConverter(
      Function<MessageDict, Integer> getter, BiConsumer<MessageDict.Builder, Integer> setter) {
    return new PlistConverter() {
      @Override
      public NSObject getValueAsPlistObject(MessageDict dict) {
        return new NSNumber(getter.apply(dict));
      }

      @Override
      public void setValueFromPlistObject(Builder dict, NSObject plistObject) {
        setter.accept(dict, ((NSNumber) plistObject).intValue());
      }
    };
  }

  private static PlistConverter jsonConverter(
      Function<MessageDict, JsonObject> getter,
      BiConsumer<MessageDict.Builder, JsonObject> setter) {
    return new PlistConverter() {
      @Override
      public NSObject getValueAsPlistObject(MessageDict dict) {
        return new NSData(getter.apply(dict).toString().getBytes(UTF_8));
      }

      @Override
      public void setValueFromPlistObject(MessageDict.Builder dict, NSObject plistObject) {
        String jsonString = new String(((NSData) plistObject).bytes(), UTF_8);
        setter.accept(dict, JavaxJson.parseObject(jsonString));
      }
    };
  }

  private static PlistConverter stringConverter(
      Function<MessageDict, String> getter, BiConsumer<MessageDict.Builder, String> setter) {
    return new PlistConverter() {
      @Override
      public NSObject getValueAsPlistObject(MessageDict dict) {
        return new NSString(getter.apply(dict));
      }

      @Override
      public void setValueFromPlistObject(MessageDict.Builder dict, NSObject plistObject) {
        setter.accept(dict, ((NSString) plistObject).getContent());
      }
    };
  }
}
