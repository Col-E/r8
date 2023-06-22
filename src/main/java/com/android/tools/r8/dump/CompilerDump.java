// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dump;

import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.ZipUtils;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class CompilerDump {

  private final Path directory;

  public static CompilerDump fromArchive(Path dumpArchive, Path dumpExtractionDirectory)
      throws IOException {
    ZipUtils.unzip(dumpArchive, dumpExtractionDirectory);
    return new CompilerDump(dumpExtractionDirectory);
  }

  public CompilerDump(Path directory) {
    this.directory = directory;
  }

  public Path getProgramArchive() {
    return directory.resolve("program.jar");
  }

  public Path getClasspathArchive() {
    return directory.resolve("classpath.jar");
  }

  public Path getLibraryArchive() {
    return directory.resolve("library.jar");
  }

  public Path getBuildPropertiesFile() {
    return directory.resolve("build.properties");
  }

  public Path getProguardConfigFile() {
    return directory.resolve("proguard.config");
  }

  public Path getDesugaredLibraryFile() {
    return directory.resolve("desugared-library.json");
  }

  public void sanitizeProguardConfig(ProguardConfigSanitizer sanitizer) throws IOException {
    try (BufferedReader reader = Files.newBufferedReader(getProguardConfigFile())) {
      String next = reader.readLine();
      while (next != null) {
        sanitizer.sanitize(next);
        next = reader.readLine();
      }
    }
  }

  public DumpOptions getBuildProperties() throws IOException {
    if (Files.exists(getBuildPropertiesFile())) {
      DumpOptions.Builder builder = new DumpOptions.Builder();
      DumpOptions.parse(
          FileUtils.readTextFile(getBuildPropertiesFile(), StandardCharsets.UTF_8), builder);
      return builder.build();
    }
    return null;
  }
}
