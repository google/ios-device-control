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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assert_;

import com.google.common.base.Predicates;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.google.iosdevicecontrol.real.DevDiskImages.DiskImage;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for the {@link com.google.iosdevicecontrol.real.DevDiskImages}. */
@RunWith(JUnit4.class)
public class DevDiskImagesTest {
  @Test
  public void findWithMaximumVersionMatch() throws IOException {
    DevDiskImages devDiskImages = findWithFakeImageDirs("5.1", "6.1 (1ACFS)", "6.1.1");
    DiskImage diskImage = devDiskImages.findForVersion("6.1.1");
    assertThat(diskImage.imagePath().getParent().endsWith("6.1.1")).isTrue();
  }

  @Test
  public void findWithMinimumVersionMatch() throws IOException {
    DevDiskImages devDiskImages = findWithFakeImageDirs("5.1", "6.1 (1ACFS)");
    DiskImage diskImage = devDiskImages.findForVersion("6.1.1");
    assertThat(diskImage.imagePath().getParent().endsWith("6.1 (1ACFS)")).isTrue();
  }

  @Test
  public void findWithNoVersionMatchFails() throws IOException {
    DevDiskImages devDiskImages = findWithFakeImageDirs("5.1", "6.0 (1ACFS)");
    assertFindFails(devDiskImages, "6.1.1");
  }

  @Test
  public void findWithMissingImageFileFails() throws IOException {
    DevDiskImages devDiskImages = findWithFakeImageDirs("8.1");
    DiskImage diskImage = devDiskImages.findForVersion("8.1");
    Files.delete(diskImage.imagePath());
    assertFindFails(devDiskImages, "8.1");
  }

  @Test
  public void findWithMissingSignatureFileFails() throws IOException {
    DevDiskImages devDiskImages = findWithFakeImageDirs("8.2");
    DiskImage diskImage = devDiskImages.findForVersion("8.2");
    Files.delete(diskImage.signaturePath());
    assertFindFails(devDiskImages, "8.2");
  }

  @Test
  public void findWithMultipleImageFilesFails() throws IOException {
    DevDiskImages devDiskImages = findWithFakeImageDirs("3.1.2");
    DiskImage diskImage = devDiskImages.findForVersion("3.1.2");
    createCopyOf(diskImage.imagePath());
    assertFindFails(devDiskImages, "3.1.2");
  }

  @Test
  public void findWithMultipleSignatureFilesFails() throws IOException {
    DevDiskImages devDiskImages = findWithFakeImageDirs("10.0");
    DiskImage diskImage = devDiskImages.findForVersion("10.0");
    createCopyOf(diskImage.signaturePath());
    assertFindFails(devDiskImages, "10.0");
  }

  @Test
  public void findWithUnreadableImageFilesFails() throws IOException {
    FakeReadable fakeReadable = new FakeReadable();
    DevDiskImages devDiskImages = findWithFakeImageDirs(fakeReadable, "5.2");
    DiskImage diskImage = devDiskImages.findForVersion("5.2");
    fakeReadable.unreadable.add(diskImage.imagePath());
    assertFindFails(devDiskImages, "5.2");
  }

  @Test
  public void findWithUnreadableSignatureFilesFails() throws IOException {
    FakeReadable fakeReadable = new FakeReadable();
    DevDiskImages devDiskImages = findWithFakeImageDirs(fakeReadable, "2.5");
    DiskImage diskImage = devDiskImages.findForVersion("2.5");
    fakeReadable.unreadable.add(diskImage.imagePath());
    assertFindFails(devDiskImages, "2.5");
  }

  private static DevDiskImages findWithFakeImageDirs(String... imageDirNames) throws IOException {
    return findWithFakeImageDirs(Predicates.alwaysTrue(), imageDirNames);
  }

  private static DevDiskImages findWithFakeImageDirs(
      Predicate<Path> readablePredicate, String... imageDirNames) throws IOException {
    FileSystem fileSystem =
        Jimfs.newFileSystem(Configuration.unix().toBuilder().setAttributeViews("posix").build());
    Path rootImagesDir = fileSystem.getPath("path", "to", "images");
    Files.createDirectories(rootImagesDir);
    for (String imageDirName : imageDirNames) {
      Path imageDir = rootImagesDir.resolve(imageDirName);
      Files.createDirectory(imageDir);
      Files.createFile(imageDir.resolve("DiskImage." + DevDiskImages.IMAGE_EXTENSION));
      Files.createFile(imageDir.resolve("DiskImage." + DevDiskImages.SIGNATURE_EXTENSION));
    }
    return DevDiskImages.forTesting(rootImagesDir, readablePredicate);
  }

  private static final class FakeReadable implements Predicate<Path> {
    private final Set<Path> unreadable = new HashSet<>();

    @Override
    public boolean test(Path p) {
      return !unreadable.contains(p);
    }
  }

  private static void createCopyOf(Path file) throws IOException {
    Files.copy(file, file.getParent().resolve("CopyOf" + file.getFileName()));
  }

  private static void assertFindFails(DevDiskImages devDiskImages, String versionString) {
    try {
      devDiskImages.findForVersion(versionString);
      assert_().fail();
    } catch (IllegalStateException expected) {}
  }
}
