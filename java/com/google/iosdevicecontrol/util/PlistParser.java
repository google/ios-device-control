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

package com.google.iosdevicecontrol.util;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.dd.plist.BinaryPropertyListParser;
import com.dd.plist.NSObject;
import com.dd.plist.PropertyListFormatException;
import com.dd.plist.PropertyListParser;
import com.dd.plist.XMLPropertyListParser;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.SAXException;

/** Utilities for parsing plists. */
public class PlistParser {
  /**
   * Parses the content of a plist file as UTF-8 encoded XML.
   *
   * @throws PlistParseException - if there is an error parsing the content.
   */
  public static NSObject fromPath(Path plist) {
    try {
      return PropertyListParser.parse(Files.readAllBytes(plist));
    } catch (ParserConfigurationException
        | ParseException
        | PropertyListFormatException
        | IOException
        | SAXException e) {
      throw new PlistParseException(e);
    }
  }

  /**
   * Parses an XML string to a plist object.
   *
   * @throws PlistParseException - if there is an error parsing the string.
   */
  public static NSObject fromXml(String xml) {
    try {
      return XMLPropertyListParser.parse(xml.getBytes(UTF_8));
    } catch (ParserConfigurationException
        | ParseException
        | PropertyListFormatException
        | IOException
        | SAXException e) {
      throw new PlistParseException(e);
    }
  }

  /**
   * Parses a byte array to a plist object.
   *
   * @throws PlistParseException - if there is an error parsing the bytes.
   */
  public static NSObject fromBinary(byte[] bytes) {
    try {
      return BinaryPropertyListParser.parse(bytes);
    } catch (UnsupportedEncodingException | PropertyListFormatException e) {
      throw new PlistParseException(e);
    }
  }

  /** Thrown if a parsing error occurs. */
  public static class PlistParseException extends RuntimeException {
    private PlistParseException(Exception cause) {
      super(cause);
    }
  }

  private PlistParser() {}
}
