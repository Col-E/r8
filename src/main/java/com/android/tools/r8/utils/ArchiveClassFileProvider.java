// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import static com.android.tools.r8.utils.FileUtils.CLASS_EXTENSION;
import static com.android.tools.r8.utils.FileUtils.isArchive;
import static com.android.tools.r8.utils.FileUtils.isClassFile;

import com.android.tools.r8.ClassFileResourceProvider;
import com.android.tools.r8.Resource;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.shaking.FilteredClassPath;
import com.google.common.io.ByteStreams;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Lazy Java class file resource provider loading class files form a zip archive.
 */
public final class ArchiveClassFileProvider implements ClassFileResourceProvider {

  private final Set<String> descriptors = new HashSet<>();
  private final ZipFile zipFile;

  public static ClassFileResourceProvider fromArchive(FilteredClassPath archive)
      throws IOException {
    return new ArchiveClassFileProvider(archive);
  }

  // Guess class descriptor from location of the class file.
  static String guessTypeDescriptor(Path name) {
    return guessTypeDescriptor(name.toString());
  }

  // Guess class descriptor from location of the class file.
  private static String guessTypeDescriptor(String name) {
    assert name != null;
    assert name.endsWith(CLASS_EXTENSION) :
        "Name " + name + " must have " + CLASS_EXTENSION + " suffix";
    String fileName =
        File.separatorChar == '/' ? name.toString() :
            name.toString().replace(File.separatorChar, '/');
    String descriptor = fileName.substring(0, fileName.length() - CLASS_EXTENSION.length());
    if (descriptor.contains(".")) {
      throw new CompilationError("Unexpected file name in the archive: " + fileName);
    }
    return 'L' + descriptor + ';';
  }

  private ArchiveClassFileProvider(FilteredClassPath archive) throws IOException {
    assert isArchive(archive.getPath());
    zipFile = new ZipFile(archive.getPath().toFile());
    final Enumeration<? extends ZipEntry> entries = zipFile.entries();
    while (entries.hasMoreElements()) {
      ZipEntry entry = entries.nextElement();
      String name = entry.getName();
      Path entryPath = Paths.get(name);
      if (isClassFile(entryPath) && archive.matchesFile(entryPath)) {
        descriptors.add(guessTypeDescriptor(name));
      }
    }
  }

  @Override
  public Set<String> getClassDescriptors() {
    return Collections.unmodifiableSet(descriptors);
  }

  @Override
  public Resource getResource(String descriptor) {
    if (!descriptors.contains(descriptor)) {
      return null;
    }

    try (InputStream inputStream = zipFile.getInputStream(getZipEntryFromDescriptor(descriptor))) {
      return Resource.fromBytes(
          Resource.Kind.CLASSFILE,
          ByteStreams.toByteArray(inputStream),
          Collections.singleton(descriptor));
    } catch (IOException e) {
      throw new CompilationError(
          "Failed to read '" + descriptor + "' from '" + zipFile.getName() + "'");
    }
  }

  @Override
  protected void finalize() throws Throwable {
    zipFile.close();
    super.finalize();
  }

  @Override
  public String toString() {
    return descriptors.size() + " resources from '" + zipFile.getName() +"'";
  }

  private ZipEntry getZipEntryFromDescriptor(String descriptor) {
    return zipFile.getEntry(descriptor.substring(1, descriptor.length() - 1) + CLASS_EXTENSION);
  }
}
