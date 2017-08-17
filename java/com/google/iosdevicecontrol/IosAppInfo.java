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

import com.dd.plist.NSDictionary;
import com.dd.plist.NSObject;
import com.google.auto.value.AutoValue;
import com.google.common.collect.MoreCollectors;
import com.google.common.io.MoreFiles;
import com.google.iosdevicecontrol.util.PlistParser;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

/** Information about an iOS application. */
@AutoValue
public abstract class IosAppInfo {
  /** Returns the application info read from either an app folder or ipa archive */
  public static IosAppInfo readFromPath(Path ipaOrAppPath) throws IOException {
    NSObject plistDict;
    if (Files.isDirectory(ipaOrAppPath)) {
      plistDict = PlistParser.fromPath(ipaOrAppPath.resolve("Info.plist"));
    } else {
      try (FileSystem ipaFs = FileSystems.newFileSystem(ipaOrAppPath, null)) {
        Path appPath =
            MoreFiles.listFiles(ipaFs.getPath("Payload"))
                .stream()
                // Can't use Files.isDirectory, because no entry is a "directory" in a zip.
                .filter(e -> e.toString().endsWith(".app/"))
                .collect(MoreCollectors.onlyElement());
        plistDict = PlistParser.fromPath(appPath.resolve("Info.plist"));
      }
    }
    return readFromPlistDictionary((NSDictionary) plistDict);
  }

  /** Returns the application info read from a plist dictionary. */
  public static IosAppInfo readFromPlistDictionary(NSDictionary dict) {
    return builder()
        .bundleId(new IosAppBundleId(dict.get("CFBundleIdentifier").toString()))
        .build();
  }

  /** Returns a new builder. */
  public static Builder builder() {
    return new AutoValue_IosAppInfo.Builder();
  }

  /** A builder for {@code IosAppInfo}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder bundleId(IosAppBundleId bundleId);

    public abstract IosAppInfo build();
  }

  /** The bundle identifier of the application. */
  public abstract IosAppBundleId bundleId();
}
