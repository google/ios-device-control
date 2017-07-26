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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A wrapper to a Java resource, for easy and memoized conversion to a path on disk.
 */
@AutoValue
public abstract class Resource {
  private static final ClassLoader CLASS_LOADER = Resource.class.getClassLoader();
  private static final Interner<Resource> INTERNER = Interners.newStrongInterner();

  /** The default strategy for copying a resource to a file system path. */
  private static final ResourceToPathCopier DEFAULT_COPIER =
      new ResourceToPathCopier() {
        @Override
        public Path copy(String resourceName) throws IOException {
          InputStream stream = CLASS_LOADER.getResourceAsStream(resourceName);
          // On some OS's the resource separator '/' differs from the file separator.
          Path path = RESOURCE_ROOT.get().resolve(resourceName.replace('/', File.separatorChar));
          Files.createDirectories(path.getParent());
          Files.copy(stream, path);
          return path;
        }
      };

  /**
   * Returns the Java resource with the specified name.
   *
   * @throws IllegalArgumentException - if a resource of that name does not exist
   */
  public static Resource named(String name) {
    return Resource.named(name, DEFAULT_COPIER);
  }

  /**
   * Returns the name of the resource.
   */
  public abstract String name();

  private final LazyPath path =
      new LazyPath() {
        @Override
        Path create() throws IOException {
          return copier().copy(name());
        }
      };

  /**
   * Extracts the resource to a path on the default filesystem.
   *
   * @throws IOException - if an I/O error occurs
   */
  public Path toPath() throws IOException {
    return path.get();
  }

  /**
   * Temporary directory under which we will extract all resources.
   */
  private static final LazyPath RESOURCE_ROOT =
      new LazyPath() {
        @Override
        Path create() throws IOException {
          return Files.createTempDirectory("resources");
        }
      };

  /**
   * A path on the default file system that is lazily created.
   */
  private abstract static class LazyPath {
    private final CheckedCallable<Path, IOException> callable =
        CheckedCallables.memoize(
            new CheckedCallable<Path, IOException>() {
              @Override
              public Path call() throws IOException {
                return create();
              }
            });

    private final Path get() throws IOException {
      return callable.call();
    }

    abstract Path create() throws IOException;
  }

  //
  // The following members exist only to mock out the resource copying for testing.
  //

  @VisibleForTesting
  static Resource named(String name, ResourceToPathCopier copier) {
    checkArgument(CLASS_LOADER.getResource(name) != null, "Resource does not exist: %s", name);
    return INTERNER.intern(new AutoValue_Resource(name, copier));
  }

  @VisibleForTesting
  abstract ResourceToPathCopier copier();

  @VisibleForTesting
  interface ResourceToPathCopier {
    Path copy(String resourceName) throws IOException;
  }
}
