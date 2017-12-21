// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import static com.android.tools.r8.utils.FileUtils.CLASS_EXTENSION;
import static com.android.tools.r8.utils.FileUtils.isArchive;
import static com.android.tools.r8.utils.FileUtils.isClassFile;

import com.android.tools.r8.ClassFileResourceProvider;
import com.android.tools.r8.ProgramResource;
import com.android.tools.r8.ProgramResource.Kind;
import com.android.tools.r8.Resource;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.origin.ArchiveEntryOrigin;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.shaking.FilteredClassPath;
import com.google.common.io.ByteStreams;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Lazy Java class-file resource provider for loading class files from a zip archive modulo a class
 * filter.
 *
 * <p>This is an internal class and should not be relied upon externally. Use {@link
 * com.android.tools.r8.ArchiveClassFileProvider}.
 */
public class FilteredArchiveClassFileProvider implements ClassFileResourceProvider, Closeable {

  private final Origin origin;
  private final Set<String> descriptors = new HashSet<>();
  private final ZipFile zipFile;

  static ClassFileResourceProvider fromArchive(FilteredClassPath archive) throws IOException {
    return new FilteredArchiveClassFileProvider(archive);
  }

  protected FilteredArchiveClassFileProvider(FilteredClassPath archive) throws IOException {
    assert isArchive(archive.getPath());
    origin = new PathOrigin(archive.getPath());
    try {
      zipFile = new ZipFile(archive.getPath().toFile());
    } catch (IOException e) {
      if (!Files.exists(archive.getPath())) {
        throw new NoSuchFileException(archive.getPath().toString());
      } else {
        throw e;
      }
    }
    final Enumeration<? extends ZipEntry> entries = zipFile.entries();
    while (entries.hasMoreElements()) {
      ZipEntry entry = entries.nextElement();
      String name = entry.getName();
      Path entryPath = Paths.get(name);
      if (isClassFile(entryPath) && archive.matchesFile(entryPath)) {
        descriptors.add(DescriptorUtils.guessTypeDescriptor(name));
      }
    }
  }

  @Override
  public Set<String> getClassDescriptors() {
    return Collections.unmodifiableSet(descriptors);
  }

  @Override
  @Deprecated
  public Resource getResource(String descriptor) {
    return getProgramResource(descriptor);
  }

  @Override
  public ProgramResource getProgramResource(String descriptor) {
    if (!descriptors.contains(descriptor)) {
      return null;
    }

    ZipEntry zipEntry = getZipEntryFromDescriptor(descriptor);
    try (InputStream inputStream = zipFile.getInputStream(zipEntry)) {
      return ProgramResource.fromBytes(
          new ArchiveEntryOrigin(zipEntry.getName(), origin),
          Kind.CF,
          ByteStreams.toByteArray(inputStream),
          Collections.singleton(descriptor));
    } catch (IOException e) {
      throw new CompilationError("Failed to read '" + descriptor, origin);
    }
  }

  @Override
  protected void finalize() throws Throwable {
    close();
    super.finalize();
  }

  @Override
  public void close() throws IOException {
    zipFile.close();
  }

  private ZipEntry getZipEntryFromDescriptor(String descriptor) {
    return zipFile.getEntry(descriptor.substring(1, descriptor.length() - 1) + CLASS_EXTENSION);
  }
}
