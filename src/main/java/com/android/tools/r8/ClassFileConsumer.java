// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.android.tools.r8.utils.FileUtils.CLASS_EXTENSION;

import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.IOExceptionDiagnostic;
import com.android.tools.r8.utils.ZipUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.ZipOutputStream;

/**
 * Consumer for Java classfile encoded programs.
 *
 * <p>This consumer can only be provided to R8.
 */
public interface ClassFileConsumer extends ProgramConsumer {

  /**
   * Callback to receive Java classfile data for a compilation output.
   *
   * <p>There is no guaranteed order and files might be written concurrently.
   *
   * <p>The consumer is expected not to throw, but instead report any errors via the diagnostics
   * {@param handler}. If an error is reported via {@param handler} and no exceptions are thrown,
   * then the compiler guaranties to exit with an error.
   *
   * @param data Java class-file encoded data.
   * @param descriptor Class descriptor of the class the data pertains to.
   * @param handler Diagnostics handler for reporting.
   */
  void accept(byte[] data, String descriptor, DiagnosticsHandler handler);

  /** Empty consumer to request the production of the resource but ignore its value. */
  static ClassFileConsumer emptyConsumer() {
    return ForwardingConsumer.EMPTY_CONSUMER;
  }

  /** Forwarding consumer to delegate to an optional existing consumer. */
  class ForwardingConsumer implements ClassFileConsumer {

    private static final ClassFileConsumer EMPTY_CONSUMER = new ForwardingConsumer(null);

    private final ClassFileConsumer consumer;

    public ForwardingConsumer(ClassFileConsumer consumer) {
      this.consumer = consumer;
    }

    @Override
    public void accept(byte[] data, String descriptor, DiagnosticsHandler handler) {
      if (consumer != null) {
        consumer.accept(data, descriptor, handler);
      }
    }

    @Override
    public void finished(DiagnosticsHandler handler) {
      if (consumer != null) {
        consumer.finished(handler);
      }
    }

  }

  /** Archive consumer to write program resources to a zip archive. */
  class ArchiveConsumer extends ForwardingConsumer implements InternalProgramOutputPathConsumer {

    private final Path archive;
    private final Origin origin;
    private ZipOutputStream stream = null;
    private boolean closed = false;

    public ArchiveConsumer(Path archive) {
      this(archive, null);
    }

    public ArchiveConsumer(Path archive, ClassFileConsumer consumer) {
      super(consumer);
      this.archive = archive;
      origin = new PathOrigin(archive);
    }

    @Override
    public void accept(byte[] data, String descriptor, DiagnosticsHandler handler) {
      super.accept(data, descriptor, handler);
      synchronizedWrite(getClassFileName(descriptor), data, handler);
    }

    @Override
    public void finished(DiagnosticsHandler handler) {
      super.finished(handler);
      assert !closed;
      closed = true;
      try {
        if (stream != null) {
          stream.close();
          stream = null;
        }
      } catch (IOException e) {
        handler.error(new IOExceptionDiagnostic(e, origin));
      }
    }

    @Override
    public Path internalGetOutputPath() {
      return archive;
    }

    private ZipOutputStream getStream(DiagnosticsHandler handler) {
      assert !closed;
      if (stream == null) {
        try {
          stream =
              new ZipOutputStream(
                  Files.newOutputStream(
                      archive, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING));
        } catch (IOException e) {
          handler.error(new IOExceptionDiagnostic(e, origin));
        }
      }
      return stream;
    }

    private synchronized void synchronizedWrite(
        String entry, byte[] content, DiagnosticsHandler handler) {
      try {
        ZipUtils.writeToZipStream(getStream(handler), entry, content);
      } catch (IOException e) {
        handler.error(new IOExceptionDiagnostic(e, origin));
      }
    }

    private static String getClassFileName(String classDescriptor) {
      assert classDescriptor != null && DescriptorUtils.isClassDescriptor(classDescriptor);
      return DescriptorUtils.getClassBinaryNameFromDescriptor(classDescriptor) + CLASS_EXTENSION;
    }
  }

  /** Directory consumer to write program resources to a directory. */
  class DirectoryConsumer extends ForwardingConsumer implements InternalProgramOutputPathConsumer {

    private final Path directory;

    public DirectoryConsumer(Path directory) {
      this(directory, null);
    }

    public DirectoryConsumer(Path directory, ClassFileConsumer consumer) {
      super(consumer);
      this.directory = directory;
    }

    @Override
    public void accept(byte[] data, String descriptor, DiagnosticsHandler handler) {
      super.accept(data, descriptor, handler);
      Path target = directory.resolve(ArchiveConsumer.getClassFileName(descriptor));
      try {
        writeFileFromDescriptor(data, target);
      } catch (IOException e) {
        handler.error(new IOExceptionDiagnostic(e, new PathOrigin(target)));
      }
    }

    @Override
    public void finished(DiagnosticsHandler handler) {
      super.finished(handler);
    }

    @Override
    public Path internalGetOutputPath() {
      return directory;
    }

    private static void writeFileFromDescriptor(byte[] contents, Path target) throws IOException {
      Files.createDirectories(target.getParent());
      FileUtils.writeToFile(target, null, contents);
    }
  }
}
