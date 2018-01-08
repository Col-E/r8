// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.IOExceptionDiagnostic;
import com.android.tools.r8.utils.ZipUtils;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipOutputStream;

/**
 * Consumer for DEX encoded programs.
 *
 * <p>This consumer receives DEX file content using standard indexed-multidex for programs larger
 * than a single DEX file. This is the default consumer for DEX programs.
 */
public interface DexIndexedConsumer extends ProgramConsumer {

  /**
   * Callback to receive DEX data for a compilation output.
   *
   * <p>This is the equivalent to writing out the files classes.dex, classes2.dex, etc., where
   * fileIndex gives the current file count (with the first file having index zero).
   *
   * <p>There is no guaranteed order and files might be written concurrently.
   *
   * <p>The consumer is expected not to throw, but instead report any errors via the diagnostics
   * {@param handler}. If an error is reported via {@param handler} and no exceptions are thrown,
   * then the compiler guaranties to exit with an error.
   *
   * @param fileIndex Index of the DEX file for multi-dexing. Files are zero-indexed.
   * @param data DEX encoded data.
   * @param descriptors Class descriptors for all classes defined in the DEX data.
   * @param handler Diagnostics handler for reporting.
   */
  void accept(int fileIndex, byte[] data, Set<String> descriptors, DiagnosticsHandler handler);

  /** Empty consumer to request the production of the resource but ignore its value. */
  static DexIndexedConsumer emptyConsumer() {
    return ForwardingConsumer.EMPTY_CONSUMER;
  }

  /** Forwarding consumer to delegate to an optional existing consumer. */
  class ForwardingConsumer implements DexIndexedConsumer {

    private static final DexIndexedConsumer EMPTY_CONSUMER = new ForwardingConsumer(null);

    private final DexIndexedConsumer consumer;

    public ForwardingConsumer(DexIndexedConsumer consumer) {
      this.consumer = consumer;
    }

    @Override
    public void accept(
        int fileIndex, byte[] data, Set<String> descriptors, DiagnosticsHandler handler) {
      if (consumer != null) {
        consumer.accept(fileIndex, data, descriptors, handler);
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

    private static String getDefaultDexFileName(int fileIndex) {
      return fileIndex == 0
          ? "classes.dex"
          : ("classes" + (fileIndex + 1) + FileUtils.DEX_EXTENSION);
    }

    private final Path archive;
    private final Origin origin;
    private ZipOutputStream stream = null;
    private boolean closed = false;

    public ArchiveConsumer(Path archive) {
      this(archive, null);
    }

    public ArchiveConsumer(Path archive, DexIndexedConsumer consumer) {
      super(consumer);
      this.archive = archive;
      origin = new PathOrigin(archive);
    }

    protected String getDexFileName(int fileIndex) {
      return getDefaultDexFileName(fileIndex);
    }

    @Override
    public void accept(
        int fileIndex, byte[] data, Set<String> descriptors, DiagnosticsHandler handler) {
      super.accept(fileIndex, data, descriptors, handler);
      synchronizedWrite(getDexFileName(fileIndex), data, handler);
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

    /** Get or open the zip output stream. */
    protected synchronized ZipOutputStream getStream(DiagnosticsHandler handler) {
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

    public static void writeResources(Path archive, List<ProgramResource> resources)
        throws IOException, ResourceException {
      OpenOption[] options =
          new OpenOption[] {StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING};
      try (Closer closer = Closer.create()) {
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(archive, options))) {
          for (int i = 0; i < resources.size(); i++) {
            ProgramResource resource = resources.get(i);
            String entryName = getDefaultDexFileName(i);
            byte[] bytes = ByteStreams.toByteArray(closer.register(resource.getByteStream()));
            ZipUtils.writeToZipStream(out, entryName, bytes);
          }
        }
      }
    }

    @Override
    public Path internalGetOutputPath() {
      return archive;
    }

  }

  /** Directory consumer to write program resources to a directory. */
  class DirectoryConsumer extends ForwardingConsumer implements InternalProgramOutputPathConsumer {

    private final Path directory;
    private boolean preparedDirectory = false;

    public DirectoryConsumer(Path directory) {
      this(directory, null);
    }

    public DirectoryConsumer(Path directory, DexIndexedConsumer consumer) {
      super(consumer);
      this.directory = directory;
    }

    @Override
    public void accept(
        int fileIndex, byte[] data, Set<String> descriptors, DiagnosticsHandler handler) {
      super.accept(fileIndex, data, descriptors, handler);
      Path target = getTargetDexFile(directory, fileIndex);
      try {
        prepareDirectory();
        writeFile(data, target);
      } catch (IOException e) {
        handler.error(new IOExceptionDiagnostic(e, new PathOrigin(target)));
      }
    }

    @Override
    public void finished(DiagnosticsHandler handler) {
      super.finished(handler);
    }

    private synchronized void prepareDirectory() throws IOException {
      if (preparedDirectory) {
        return;
      }
      preparedDirectory = true;
      deleteClassesDexFiles(directory);
    }

    public static void deleteClassesDexFiles(Path directory) throws IOException {
      try (Stream<Path> filesInDir = Files.list(directory)) {
        for (Path path : filesInDir.collect(Collectors.toList())) {
          if (FileUtils.isClassesDexFile(path)) {
            Files.delete(path);
          }
        }
      }
    }

    public static void writeResources(Path directory, List<ProgramResource> resources)
        throws IOException, ResourceException {
      deleteClassesDexFiles(directory);
      try (Closer closer = Closer.create()) {
        for (int i = 0; i < resources.size(); i++) {
          ProgramResource resource = resources.get(i);
          Path target = getTargetDexFile(directory, i);
          writeFile(ByteStreams.toByteArray(closer.register(resource.getByteStream())), target);
        }
      }
    }

    private static Path getTargetDexFile(Path directory, int fileIndex) {
      return directory.resolve(ArchiveConsumer.getDefaultDexFileName(fileIndex));
    }

    private static void writeFile(byte[] contents, Path target) throws IOException {
      Files.createDirectories(target.getParent());
      FileUtils.writeToFile(target, null, contents);
    }

    @Override
    public Path internalGetOutputPath() {
      return directory;
    }

  }
}
