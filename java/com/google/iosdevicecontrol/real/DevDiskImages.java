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

package com.google.iosdevicecontrol.real;

import com.google.auto.value.AutoValue;
import com.google.common.base.Splitter;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * Finds disk images for an iOS product version within a directory that has the structure of the
 * Xcode iPhoneOS.platform/DeviceSupport directory. That is, within this directory is any number of
 * image subdirectories with the name of an iOS product version. Then each of these subdirectories
 * contain a single image and single signature file.
 */
@AutoValue
abstract class DevDiskImages {
  private static final Pattern VERSION_PATTERN = Pattern.compile("^(\\d+(?:\\.\\d+)+)");
  private static final Splitter VERSION_NUM_SPLITTER = Splitter.on('.');

  static final String IMAGE_EXTENSION = "dmg";
  static final String SIGNATURE_EXTENSION = "signature";

  private static final Predicate<Path> NATIVE_READABLE =  path -> Files.isReadable(path);

  static DevDiskImages inDirectory(Path rootImagesDir) {
    return forTesting(rootImagesDir, NATIVE_READABLE);
  }

  static DevDiskImages forTesting(Path rootImagesDir, Predicate<Path> readablePredicate) {
    return new AutoValue_DevDiskImages(rootImagesDir, readablePredicate);
  }

  abstract Path rootImagesDirectory();

  abstract Predicate<Path> readablePredicate();

  /**
   * Finds the disk image matching the specified iOS version.
   *
   * @throws IllegalStateException - if a matching developer disk image cannot be found
   */
  DiskImage findForVersion(String iosVersion) {
    Iterable<String> deviceVersionNums = splitVersionString(iosVersion);
    if (deviceVersionNums == null) {
      throw new IllegalArgumentException("Invalid product version string: " + iosVersion);
    }

    // Find the image directory that matches the greatest number of version number components.
    Path bestMatchingImageDir = null;
    int maxMatchingVersionNums = 0;
    try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(rootImagesDirectory())) {
      for (Path imageDir : dirStream) {
        Iterable<String> dirVersionNums = splitVersionString(imageDir.getFileName().toString());
        if (dirVersionNums != null) {
          int matchingVersionNums = lengthCommonPrefix(deviceVersionNums, dirVersionNums);
          // A matching image directory must match at least two version number components.
          if (matchingVersionNums > maxMatchingVersionNums && matchingVersionNums > 1) {
            bestMatchingImageDir = imageDir;
            maxMatchingVersionNums = matchingVersionNums;
          }
        }
      }
    } catch (IOException e) {
      throw new IllegalStateException("Error finding developer disk image", e);
    }

    if (bestMatchingImageDir == null) {
      throw new IllegalStateException("No disk image directory found for version: " + iosVersion);
    }

    Path image = findImageFileWithExtension(bestMatchingImageDir, IMAGE_EXTENSION);
    Path signature = findImageFileWithExtension(bestMatchingImageDir, SIGNATURE_EXTENSION);
    return new AutoValue_DevDiskImages_DiskImage(image, signature);
  }

  @AutoValue
  abstract static class DiskImage {
    abstract Path imagePath();
    abstract Path signaturePath();
  }

  private Path findImageFileWithExtension(Path dir, String ext) {
    Path file = null;
    try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(dir, "*." + ext)) {
      Iterator<Path> imageIter = dirStream.iterator();
      if (!imageIter.hasNext()) {
        throw new IllegalStateException("No " + ext + " image file in " + dir);
      }
      file = imageIter.next();
      if (imageIter.hasNext()) {
        throw new IllegalStateException("Multiple " + ext + " image files in " + dir);
      }
    } catch (IOException e) {
      throw new IllegalStateException("Error finding " + ext + " image file in " + dir, e);
    }
    if (!readablePredicate().test(file)) {
      throw new IllegalStateException("Image file is not not readable: " + file);
    }
    return file;
  }

  @Nullable private static Iterable<String> splitVersionString(String versionString) {
    Matcher matcher = VERSION_PATTERN.matcher(versionString);
    return matcher.find() ? VERSION_NUM_SPLITTER.split(matcher.group(1)) : null;
  }

  private static int lengthCommonPrefix(Iterable<?> i1, Iterable<?> i2) {
    int numMatching = 0;
    Iterator<?> iter1 = i1.iterator();
    Iterator<?> iter2 = i2.iterator();
    while (iter1.hasNext() && iter2.hasNext() && iter1.next().equals(iter2.next())) {
      numMatching++;
    }
    return numMatching;
  }
}
