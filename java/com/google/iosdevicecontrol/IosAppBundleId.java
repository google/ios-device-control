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

import static com.google.common.base.Preconditions.checkArgument;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * An iOS application bundle identifier; normally a reverse DNS string, e.g. com.apple.mobilesafari.
 */
public final class IosAppBundleId {
  // Implementation note: I didn't use @AutoValue, because I didn't want to add an additional
  // property method to this class beyond toString. See: https://github.com/google/auto/issues/357

  private static final Pattern VALID_UTI_PATTERN = Pattern.compile("^[\\w-\\.]+$");

  /**
   * Returns the bundle identifier read from a ".ipa" application archive file. This is equivalent
   * to {@code IosAppInfo.readFromIpa(ipaPath).bundleId()}.
   */
  public static IosAppBundleId readFromIpa(Path ipaPath) throws IOException {
    return IosAppInfo.readFromPath(ipaPath).bundleId();
  }

  private final String string;

  /**
   * Constructs a bundle ID from the given string. This constructor checks that the specified string
   * is a valid Apple Uniform Type Identifier (UTI), meaning it contains only alphanumeric
   * (A-Z,a-z,0-9), hyphen (-), and period (.) characters.
   *
   * @throws IllegalArgumentException if the string is not a valid Apple UTI.
   */
  public IosAppBundleId(String string) {
    checkArgument(VALID_UTI_PATTERN.matcher(string).matches());
    this.string = string;
  }

  @Override
  public String toString() {
    return string;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(string);
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof IosAppBundleId && string.equals(((IosAppBundleId) o).string);
  }
}
