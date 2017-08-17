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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assert_;

import com.google.iosdevicecontrol.util.Resource.ResourceToPathCopier;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link com.google.iosdevicecontrol.util.Resource}. */
@RunWith(JUnit4.class)
public class ResourceTest {

  @Test
  public void testCanExtractResourceToPath() throws IOException {
    Resource resource = Resource.named("com/google/iosdevicecontrol/util/resource1");
    Path path = resource.toPath();
    assertThat(Files.exists(path)).isTrue();
  }

  @Test
  public void testExtractFailsWhenFileCopyingFails() {
    Resource resource =
        Resource.named(
            "com/google/iosdevicecontrol/util/resource1",
            new ResourceToPathCopier() {
              @Override
              public Path copy(String name) throws IOException {
                throw new IOException();
              }
            });
    try {
      resource.toPath();
      assert_().fail();
    } catch (IOException expected) {
    }
  }

  @Test
  public void testCannotConstructNonExistentResource() {
    try {
      Resource.named("i/do/not/exist");
      assert_().fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void testResourcesWithDifferentNamesAreNotEqual() {
    Resource resource1 = Resource.named("com/google/iosdevicecontrol/util/resource1");
    Resource resource2 = Resource.named("com/google/iosdevicecontrol/util/resource2");
    assertThat(resource1).isNotEqualTo(resource2);
  }

  @Test
  public void testResourcesWithSameNameAreTheSameInstance() {
    Resource resource1a = Resource.named("com/google/iosdevicecontrol/util/resource1");
    Resource resource1b = Resource.named("com/google/iosdevicecontrol/util/resource1");
    assertThat(resource1a).isSameAs(resource1b);
  }
}
