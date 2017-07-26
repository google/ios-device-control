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

/**
 * Simple text formatting class which makes it easy to truncate
 * strings using an ellipsis ("..."). Supports a number of truncation
 * styles depending on what is desired.
 *
 * <p>Note that this class is not fully Unicode aware and counts characters
 * rather than code points. If Unicode content is formatted using this class
 * then the number of code-points in the output may currently be smaller than
 * the {@code length} parameter. However this class is safe to use for Unicode
 * content and will never split a string in the middle of a surrogate pair.
 */
public final class EllipsisFormat {
  private static final String ELLIPSIS = "...";
  private static final int ELLIPSIS_LEN = ELLIPSIS.length();
  private final int maxLength;
  private final Style style;

  public enum Style { LEFT, RIGHT, CENTER }

  /**
   * Constructs a new format instance where the string will be
   * truncated if it is greater than the specified length. The default
   * style will be used.
   */
  public EllipsisFormat(int length) {
    this(length, Style.RIGHT);
  }

  /**
   * Constructs a new format instance where the string will be
   * truncated if it is greater than the specified length. The
   * specified style will be used.
   */
  public EllipsisFormat(int length, Style style) {
    if (length < 0) {
      throw new IllegalArgumentException(length + " < 0");
    }
    if (style == null) {
      throw new NullPointerException();
    }
    this.maxLength = length;
    this.style = style;
  }

  /**
   * Returns the truncated string, or the original string if the
   * string is not too long.
   */
  public String format(String s) {
    if (s.length() <= maxLength) {
      return s;
    }
    if (maxLength <= ELLIPSIS_LEN) {
      return ELLIPSIS;
    }
    int length = maxLength - ELLIPSIS_LEN;
    switch (style) {
      case RIGHT:
        return leftPart(s, length) + ELLIPSIS;
      case LEFT:
        return ELLIPSIS + rightPart(s, length);
      case CENTER:
        // Note: There's no risk of including the same code-point twice
        // here because length must be strictly less than s.length().
        int leftLength = length / 2;
        int rightLength = length - leftLength;
        return leftPart(s, leftLength) + ELLIPSIS + rightPart(s, rightLength);
      default:
        throw new AssertionError();
    }
  }

  /**
   * Returns a left-substring of the given string without risking splitting
   * across a surrogate pair. If a surrogate pair would be split at the given
   * length then the output is modified to include it.
   */
  private static String leftPart(String s, int len) {
    if (len > 0 && Character.isHighSurrogate(s.charAt(len - 1))) {
      len++;
    }
    return s.substring(0, len);
  }

  /**
   * Returns a right-substring of the given string without risking splitting
   * across a surrogate pair. If a surrogate pair would be split at the given
   * length then the output is modified to include it.
   */
  private static String rightPart(String s, int len) {
    int start = s.length() - len;
    if (start < s.length() && Character.isLowSurrogate(s.charAt(start))) {
      start--;
    }
    return s.substring(start);
  }
}
