// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.rules.TemporaryFolder;

public class LibrarySanitizer {

  private final Path sanitizedLibrary;
  private final Path sanitizedPgConf;

  private final List<Path> libraryFiles = new ArrayList<>();
  private final List<Path> programFiles = new ArrayList<>();
  private final List<Path> proguardConfigurationFiles = new ArrayList<>();

  public LibrarySanitizer(TemporaryFolder temp) {
    this.sanitizedLibrary = temp.getRoot().toPath().resolve("sanitized_lib.jar");
    this.sanitizedPgConf = temp.getRoot().toPath().resolve("sanitized.config");
  }

  public LibrarySanitizer assertSanitizedProguardConfigurationIsEmpty() throws IOException {
    if (sanitizedPgConf.toFile().exists()) {
      List<String> lines = FileUtils.readAllLines(sanitizedPgConf);
      for (String line : lines) {
        assertTrue(line.trim().isEmpty());
      }
    }
    return this;
  }

  public LibrarySanitizer addLibraryFiles(List<Path> libraryFiles) {
    this.libraryFiles.addAll(libraryFiles);
    return this;
  }

  public LibrarySanitizer addProgramFiles(List<Path> programFiles) {
    this.programFiles.addAll(programFiles);
    return this;
  }

  LibrarySanitizer addProguardConfigurationFiles(List<Path> proguardConfigurationFiles) {
    this.proguardConfigurationFiles.addAll(proguardConfigurationFiles);
    return this;
  }

  LibrarySanitizer addProguardConfigurationFiles(Path... proguardConfigurationFiles) {
    return addProguardConfigurationFiles(Arrays.asList(proguardConfigurationFiles));
  }

  public Path getSanitizedLibrary() {
    return sanitizedLibrary;
  }

  Path getSanitizedProguardConfiguration() {
    return sanitizedPgConf;
  }

  public LibrarySanitizer sanitize() throws IOException {
    ImmutableList.Builder<String> command =
        new ImmutableList.Builder<String>()
            .add(ToolHelper.TOOLS_DIR + "sanitize_libraries.py")
            .add(sanitizedLibrary.toString())
            .add(sanitizedPgConf.toString());
    for (Path programFile : programFiles) {
      command.add("--injar").add(programFile.toString());
    }
    for (Path libraryFile : libraryFiles) {
      command.add("--libraryjar").add(libraryFile.toString());
    }
    for (Path proguardConfigurationFile : proguardConfigurationFiles) {
      command.add("--pgconf").add(proguardConfigurationFile.toString());
    }
    ProcessResult result = ToolHelper.runProcess(new ProcessBuilder(command.build()));
    assertEquals(result.command, 0, result.exitCode);
    return this;
  }
}
