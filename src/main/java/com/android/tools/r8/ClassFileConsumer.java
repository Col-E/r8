// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.android.tools.r8.utils.FileUtils.CLASS_EXTENSION;

import com.android.tools.r8.utils.ArchiveBuilder;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.DirectoryBuilder;
import com.android.tools.r8.utils.OutputBuilder;
import com.android.tools.r8.utils.ZipUtils;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.zip.ZipEntry;
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
    public DataResourceConsumer getDataResourceConsumer() {
      return consumer != null ? consumer.getDataResourceConsumer() : null;
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

  /** Consumer to write program resources to an output. */
  class ArchiveConsumer extends ForwardingConsumer
      implements DataResourceConsumer, InternalProgramOutputPathConsumer {
    private final OutputBuilder outputBuilder;
    protected final boolean consumeDataResources;

    public ArchiveConsumer(Path archive) {
      this(archive, null, false);
    }

    public ArchiveConsumer(Path archive, boolean consumeDataResouces) {
      this(archive, null, consumeDataResouces);
    }

    public ArchiveConsumer(Path archive, ClassFileConsumer consumer) {
      this(archive, consumer, false);
    }

    public ArchiveConsumer(Path archive, ClassFileConsumer consumer, boolean consumeDataResouces) {
      super(consumer);
      this.outputBuilder = new ArchiveBuilder(archive);
      this.consumeDataResources = consumeDataResouces;
      this.outputBuilder.open();
      if (getDataResourceConsumer() != null) {
        this.outputBuilder.open();
      }
    }

    @Override
    public DataResourceConsumer getDataResourceConsumer() {
      return consumeDataResources ? this : null;
    }

    @Override
    public void accept(byte[] data, String descriptor, DiagnosticsHandler handler) {
      super.accept(data, descriptor, handler);
      outputBuilder.addFile(getClassFileName(descriptor), data, handler);
    }

    @Override
    public void accept(DataDirectoryResource directory, DiagnosticsHandler handler) {
      outputBuilder.addDirectory(directory.getName(), handler);
    }

    @Override
    public void accept(DataEntryResource file, DiagnosticsHandler handler) {
      outputBuilder.addFile(file.getName(), file, handler);
    }

    @Override
    public void finished(DiagnosticsHandler handler) {
      super.finished(handler);
      outputBuilder.close(handler);
    }

    @Override
    public Path internalGetOutputPath() {
      return outputBuilder.getPath();
    }

    private static String getClassFileName(String classDescriptor) {
      assert classDescriptor != null && DescriptorUtils.isClassDescriptor(classDescriptor);
      return DescriptorUtils.getClassBinaryNameFromDescriptor(classDescriptor) + CLASS_EXTENSION;
    }

    public static void writeResources(Path archive, List<ProgramResource> resources)
        throws IOException, ResourceException {
      OpenOption[] options =
          new OpenOption[] {StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING};
      try (Closer closer = Closer.create()) {
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(archive, options))) {
          for (ProgramResource resource : resources) {
            assert resource.getClassDescriptors().size() == 1;
            String className = resource.getClassDescriptors().iterator().next();
            String entryName = getClassFileName(className);
            byte[] bytes = ByteStreams.toByteArray(closer.register(resource.getByteStream()));
            ZipUtils.writeToZipStream(out, entryName, bytes, ZipEntry.STORED);
          }
        }
      }
    }
  }

  /** Directory consumer to write program resources to a directory. */
  class DirectoryConsumer extends ForwardingConsumer implements InternalProgramOutputPathConsumer {
    private final OutputBuilder outputBuilder;
    protected final boolean consumeDataResouces;

    public DirectoryConsumer(Path directory) {
      this(directory, null, false);
    }

    public DirectoryConsumer(Path directory, boolean consumeDataResouces) {
      this(directory, null, consumeDataResouces);
    }

    public DirectoryConsumer(Path directory, ClassFileConsumer consumer) {
      this(directory, consumer, false);
    }

    public DirectoryConsumer(
        Path directory, ClassFileConsumer consumer, boolean consumeDataResouces) {
      super(consumer);
      this.outputBuilder = new DirectoryBuilder(directory);
      this.consumeDataResouces = consumeDataResouces;
    }

    @Override
    public DataResourceConsumer getDataResourceConsumer() {
      return consumeDataResouces ? this : null;
    }

    @Override
    public void accept(byte[] data, String descriptor, DiagnosticsHandler handler) {
      super.accept(data, descriptor, handler);
      outputBuilder.addFile(ArchiveConsumer.getClassFileName(descriptor), data, handler);
    }

    @Override
    public void accept(DataDirectoryResource directory, DiagnosticsHandler handler) {
      outputBuilder.addDirectory(directory.getName(), handler);
    }

    @Override
    public void accept(DataEntryResource file, DiagnosticsHandler handler) {
      outputBuilder.addFile(file.getName(), file, handler);
    }

    @Override
    public void finished(DiagnosticsHandler handler) {
      super.finished(handler);
    }

    @Override
    public Path internalGetOutputPath() {
      return outputBuilder.getPath();
    }
  }
}
