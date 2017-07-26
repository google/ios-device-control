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
import com.google.common.io.ByteSink;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * A sink to which a command writes its output.
 */
@AutoValue
public abstract class OutputSink {
  /**
   * The kind of sink to which command output will be written.
   */
  public enum Kind {
    /** Output to a file. */
    FILE,
    /** Output appended to a file. */
    FILE_APPEND,
    /** Output to stderr of the parent JVM process; usually equivalent to {@link System#err}. */
    // TODO(user) settle on a name, perhaps via Java API Review.
    JVM_ERR,
    /** Output to stdout of the parent JVM process; usually equivalent to {@link System#out}. */
    // TODO(user) settle on a name, perhaps via Java API Review.
    JVM_OUT,
    /**
     * Output to stderr of the command process, so it may be read from
     * {@link CommandProcess#stderrStream} and {@link CommandResult#stderrBytes()}.
     */
    PROCESS_ERR,
    /**
     * Output to stdout of the command process, so it may be read from
     * {@link CommandProcess#stdoutStream} and {@link CommandResult#stdoutBytes()}.
     */
    PROCESS_OUT,
    /** Output to an output stream. */
    STREAM
  }

  /**
   * Output to stdout of the command process, so it may be read from
   * {@link CommandProcess#stdoutStream} and {@link CommandResult#stdoutBytes()}.
   */
  public static OutputSink toProcessOut() {
    return TO_PROCESS_OUT;
  }

  /**
   * Output to stderr of the command process, so it may be read from
   * {@link CommandProcess#stderrStream} and {@link CommandResult#stderrBytes()}.
   */
  public static OutputSink toProcessErr() {
    return TO_PROCESS_ERR;
  }

  /**
   * Output to stdout of the parent JVM process; usually equivalent to {@link System#out}.
   */
  public static OutputSink toJvmOut() {
    return TO_JVM_OUT;
  }

  /**
   * Output to stderr of the parent JVM process; usually equivalent to {@link System#err}.
   */
  public static OutputSink toJvmErr() {
    return TO_JVM_ERR;
  }

  /**
   * Output to the specified file, overwriting the current file at the path, if any.
   */
  public static OutputSink toFile(Path file) {
    return create(Kind.FILE, FileByteSink.create(file, false));
  }

  /**
   * Output to the specified file, optionally appended and otherwise overwritten.
   */
  public static OutputSink toFileAppend(Path file) {
    return create(Kind.FILE_APPEND, FileByteSink.create(file, true));
  }

  /**
   * Output to an output stream supplied by the specified byte sink.
   */
  public static OutputSink toStream(ByteSink byteSink) {
    return create(Kind.STREAM, byteSink);
  }

  private static OutputSink create(Kind kind, Optional<ByteSink> byteSink) {
    return new AutoValue_OutputSink(kind, byteSink);
  }

  private static OutputSink create(Kind kind, ByteSink byteSink) {
    return create(kind, Optional.of(byteSink));
  }

  /**
   * The {@link Kind} of output target.
   */
  public abstract Kind kind();

  /**
   * The file to which output is written, if {@link #kind} is {@code FILE}.
   *
   * @throws IllegalStateException - if the output target is not a file
   */
  public final Path file() {
    checkState(kind().equals(Kind.FILE) || kind().equals(Kind.FILE_APPEND),
        "Target is %s, not a file.", kind());
    return ((FileByteSink) byteSink().get()).file();
  }

  /**
   * The supplier of the stream to which output is written, if {@link #kind} is {@code STREAM}.
   *
   * @throws IllegalStateException - if the output target is not a stream
   */
  public final ByteSink streamSupplier() {
    checkState(kind().equals(Kind.STREAM), "Target is %s, not a stream.", kind());
    return byteSink().get();
  }

  /**
   * A ByteSink wrapping this output target; present when {@link #kind} is not
   * {@code PROCESS_OUT} or {@code PROCESS_ERR}.
   */
  abstract Optional<ByteSink> byteSink();

  private static final OutputSink TO_PROCESS_OUT =
      new AutoValue_OutputSink(Kind.PROCESS_OUT, Optional.<ByteSink>absent());

  private static final OutputSink TO_PROCESS_ERR =
      new AutoValue_OutputSink(Kind.PROCESS_ERR, Optional.<ByteSink>absent());

  private static final OutputSink TO_JVM_OUT =
      create(Kind.JVM_OUT, FileDescriptorByteSink.create("stdout", FileDescriptor.out));

  private static final OutputSink TO_JVM_ERR =
      create(Kind.JVM_ERR, FileDescriptorByteSink.create("stderr", FileDescriptor.err));

  @AutoValue
  abstract static class FileDescriptorByteSink extends ByteSink {
    private static FileDescriptorByteSink create(String name, FileDescriptor descriptor) {
      return new AutoValue_OutputSink_FileDescriptorByteSink(name, descriptor);
    }

    abstract String name();
    abstract FileDescriptor descriptor();

    @Override
    public OutputStream openStream() throws IOException {
      return new FilterOutputStream(new FileOutputStream(descriptor())) {
        @Override
        public void close() {
          // Do not close the (global) file descriptor.
        }
      };
    }

    @Override
    public String toString() {
      return "ByteSink(" + name() + ")";
    }
  }

  @AutoValue
  abstract static class FileByteSink extends ByteSink {
    private static FileByteSink create(Path file, boolean append) {
      return new AutoValue_OutputSink_FileByteSink(file, append);
    }

    abstract Path file();

    abstract boolean append();

    @Override
    public OutputStream openStream() throws IOException {
      return append()
          ? Files.newOutputStream(file(), StandardOpenOption.APPEND)
          : Files.newOutputStream(file());
    }

    @Override
    public String toString() {
      return "ByteSink(" + file() + (append() ? ", APPEND" : "") + ")";
    }
  }
}
