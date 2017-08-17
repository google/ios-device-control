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

package com.google.iosdevicecontrol.command;

import static com.google.common.base.Preconditions.checkState;

import com.google.auto.value.AutoValue;
import com.google.common.base.Optional;
import com.google.common.io.ByteSource;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A source from which a command reads its input.
 */
@AutoValue
public abstract class InputSource {
  /**
   * The kind of source from which command input will be read.
   */
  public enum Kind {
    /** Input from a file. */
    FILE,
    /** Input from the parent JVM process; usually equivalent to {@link System#in}. */
    // TODO(user) settle on a name, perhaps via Java API Review.
    JVM,
    /**
     * Input from the command process, so it may be written to {@link CommandProcess#stdinStream}.
     */
    // TODO(user) actually provide CommandProcess#stdinStream.
    PROCESS,
    /** Input from an input stream. */
    STREAM
  }

  /**
   * Input from the command process, so it may be written to {@link CommandProcess#stdinStream}.
   */
  public static InputSource fromProcess() {
    return FROM_PROCESS;
  }

  /**
   * Input from the parent JVM process; usually equivalent to {@link System#in}.
   */
  public static InputSource fromJvm() {
    return FROM_JVM;
  }

  /**
   * Input from the specified file.
   */
  public static InputSource fromFile(Path file) {
    return create(Kind.FILE, FileByteSource.create(file));
  }

  /**
   * Input from an input stream supplied by the specified byte source.
   */
  public static InputSource fromStream(ByteSource byteSource) {
    return create(Kind.STREAM, byteSource);
  }

  private static InputSource create(Kind kind, Optional<ByteSource> byteSource) {
    return new AutoValue_InputSource(kind, byteSource);
  }

  private static InputSource create(Kind kind, ByteSource byteSource) {
    return create(kind, Optional.of(byteSource));
  }

  /**
   * The {@link Kind} of input source.
   */
  public abstract Kind kind();

  /**
   * The file from which input is read, if {@link #kind} is {@code FILE}.
   *
   * @throws IllegalStateException - if the input source is not a file
   */
  public final Path file() {
    checkState(kind().equals(Kind.FILE), "Source is %s, not a file.", kind());
    return ((FileByteSource) byteSource().get()).file();
  }

  /**
   * The byte source which input is read, if {@link #kind} is {@code STREAM}.
   *
   * @throws IllegalStateException - if the input source is not a stream
   */
  public final ByteSource streamSupplier() {
    checkState(kind().equals(Kind.STREAM), "Source is %s, not a stream.", kind());
    return byteSource().get();
  }

  /**
   * A ByteSource wrapping this input source; present when {@link #kind} is not {@code PROCESS}.
   */
  abstract Optional<ByteSource> byteSource();

  private static final InputSource FROM_JVM = create(Kind.JVM, new ByteSource() {
    @Override
    public InputStream openStream() throws IOException {
      return new FilterInputStream(new FileInputStream(FileDescriptor.in)) {
        @Override
        public void close() {
          // Do not close the (global) file descriptor.
        }
      };
    }

    @Override
    public String toString() {
      return "ByteSource(stdin)";
    }
  });

  private static final InputSource FROM_PROCESS =
      create(Kind.PROCESS, Optional.<ByteSource>absent());

  @AutoValue
  abstract static class FileByteSource extends ByteSource {
    private static FileByteSource create(Path file) {
      return new AutoValue_InputSource_FileByteSource(file);
    }

    abstract Path file();

    @Override
    public InputStream openStream() throws IOException {
      return Files.newInputStream(file());
    }

    @Override
    public String toString() {
      return "ByteSource(" + file() + ")";
    }
  }
}
