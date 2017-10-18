// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DirectoryOutputSink extends FileSystemOutputSink {

  private final Path outputDirectory;

  public DirectoryOutputSink(Path outputDirectory, InternalOptions options) throws IOException {
    super(options);
    this.outputDirectory = outputDirectory;
    cleanUpOutputDirectory();
  }

  private void cleanUpOutputDirectory() throws IOException {
    if (getOutputMode() == OutputMode.Indexed) {
      try (Stream<Path> filesInDir = Files.list(outputDirectory)) {
        for (Path path : filesInDir.collect(Collectors.toList())) {
          if (FileUtils.isClassesDexFile(path)) {
            Files.delete(path);
          }
        }
      }
    }
  }

  @Override
  public void writeDexFile(byte[] contents, Set<String> classDescriptors, int fileId)
      throws IOException {
    Path target = outputDirectory.resolve(getOutputPath(fileId));
    Files.createDirectories(target.getParent());
    writeToFile(target, null, contents);
  }

  @Override
  public void writeDexFile(byte[] contents, Set<String> classDescriptors, String primaryClassName)
      throws IOException {
    Path target = outputDirectory.resolve(getOutputPath(primaryClassName));
    Files.createDirectories(target.getParent());
    writeToFile(target, null, contents);
  }

  @Override
  public void close() throws IOException {
    // Intentionally left empty.
  }

  private Path getOutputPath(int index) {
    String file = index == 0 ? "classes.dex" : ("classes" + (index + 1) + ".dex");
    return outputDirectory.resolve(file);
  }

  private Path getOutputPath(String classDescriptor) throws IOException {
    assert classDescriptor != null && DescriptorUtils.isClassDescriptor(classDescriptor);
    Path result = outputDirectory
        .resolve(classDescriptor.substring(1, classDescriptor.length() - 1) + ".dex");
    Files.createDirectories(result.getParent());
    return result;
  }
}
