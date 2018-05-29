// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.android.tools.r8.utils.FileUtils.isClassFile;
import static com.android.tools.r8.utils.FileUtils.isDexFile;

import com.android.tools.r8.ProgramResource.Kind;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.origin.ArchiveEntryOrigin;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.FileUtils;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/** Provider for archives of program resources. */
@KeepForSubclassing
public class ArchiveProgramResourceProvider implements ProgramResourceProvider {

  @KeepForSubclassing
  public interface ZipFileSupplier {
    ZipFile open() throws IOException;
  }

  public static boolean includeClassFileEntries(String entry) {
    return isClassFile(Paths.get(entry));
  }

  public static boolean includeDexEntries(String entry) {
    return isDexFile(Paths.get(entry));
  }

  public static boolean includeClassFileOrDexEntries(String entry) {
    Path path = Paths.get(entry);
    return isClassFile(path) || isDexFile(path);
  }

  private final Origin origin;
  private final ZipFileSupplier supplier;
  private final Predicate<String> include;

  public static ArchiveProgramResourceProvider fromArchive(Path archive) {
    return fromArchive(archive, ArchiveProgramResourceProvider::includeClassFileOrDexEntries);
  }

  public static ArchiveProgramResourceProvider fromArchive(
      Path archive, Predicate<String> include) {
    return fromSupplier(new PathOrigin(archive), () -> new ZipFile(archive.toFile()), include);
  }

  public static ArchiveProgramResourceProvider fromSupplier(
      Origin origin, ZipFileSupplier supplier) {
    return fromSupplier(
        origin, supplier, ArchiveProgramResourceProvider::includeClassFileOrDexEntries);
  }

  public static ArchiveProgramResourceProvider fromSupplier(
      Origin origin, ZipFileSupplier supplier, Predicate<String> include) {
    return new ArchiveProgramResourceProvider(origin, supplier, include);
  }

  private ArchiveProgramResourceProvider(
      Origin origin, ZipFileSupplier supplier, Predicate<String> include) {
    assert origin != null;
    assert supplier != null;
    assert include != null;
    this.origin = origin;
    this.supplier = supplier;
    this.include = include;
  }

  private List<ProgramResource> readArchive() throws IOException {
    List<ProgramResource> dexResources = new ArrayList<>();
    List<ProgramResource> classResources = new ArrayList<>();
    try (ZipFile zipFile = supplier.open()) {
      final Enumeration<? extends ZipEntry> entries = zipFile.entries();
      while (entries.hasMoreElements()) {
        ZipEntry entry = entries.nextElement();
        try (InputStream stream = zipFile.getInputStream(entry)) {
          String name = entry.getName();
          Path path = Paths.get(name);
          Origin entryOrigin = new ArchiveEntryOrigin(entry.getName(), origin);
          if (include.test(name)) {
            if (FileUtils.isDexFile(path)) {
              dexResources.add(
                  ProgramResource.fromBytes(
                      entryOrigin, Kind.DEX, ByteStreams.toByteArray(stream), null));
            } else if (isClassFile(path)) {
              String descriptor = DescriptorUtils.guessTypeDescriptor(name);
              classResources.add(
                  ProgramResource.fromBytes(
                      entryOrigin,
                      Kind.CF,
                      ByteStreams.toByteArray(stream),
                      Collections.singleton(descriptor)));
            }
          }
        }
      }
    } catch (ZipException e) {
      throw new CompilationError("Zip error while reading archive" + e.getMessage(), e, origin);
    }
    if (!dexResources.isEmpty() && !classResources.isEmpty()) {
      throw new CompilationError(
          "Cannot create android app from an archive containing both DEX and Java-bytecode content",
          origin);
    }
    return !dexResources.isEmpty() ? dexResources : classResources;
  }

  @Override
  public Collection<ProgramResource> getProgramResources() throws ResourceException {
    try {
      return readArchive();
    } catch (IOException e) {
      throw new ResourceException(origin, e);
    }
  }
}
