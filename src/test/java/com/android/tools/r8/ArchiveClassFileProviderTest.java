// Copyright (c) 2018 the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import static org.junit.Assert.assertTrue;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ArchiveClassFileProviderTest {

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  public Path createZip() throws IOException {
    Path tempRoot = temporaryFolder.getRoot().toPath();
    Path zipFile = tempRoot.resolve("zipfile.zip");
    ZipOutputStream zipStream =
        new ZipOutputStream(new FileOutputStream(zipFile.toFile()), StandardCharsets.UTF_8);
    ZipEntry entry = new ZipEntry("non-ascii:$\u02CF");
    zipStream.putNextEntry(entry);
    zipStream.write(10);
    zipStream.close();
    return zipFile;
  }

  @Test
  public void testSystemLocale() throws IOException, ResourceException {
    // Set the locale used for Paths to ASCII which will make Path creation fail
    // for non-ascii names.
    System.setProperty("sun.jnu.encoding", "ASCII");
    Path zipFile = createZip();
    new ArchiveClassFileProvider(zipFile);
    ArchiveProgramResourceProvider.fromArchive(zipFile).getProgramResources();
  }

  @Test
  public void testMultiReleaseJars() throws IOException {
    Path jar = temporaryFolder.getRoot().toPath().resolve("classes.jar");
    try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(jar))) {
      output.putNextEntry(new ZipEntry("meta-inf/9/Test.class"));
      output.closeEntry();
      output.putNextEntry(new ZipEntry("/meta-inf/9/Test.class"));
      output.closeEntry();
    }
    ArchiveClassFileProvider provider = new ArchiveClassFileProvider(jar);
    assertTrue(provider.getClassDescriptors().isEmpty());
  }
}
