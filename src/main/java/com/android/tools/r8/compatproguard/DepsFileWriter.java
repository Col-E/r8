// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.compatproguard;

import com.android.tools.r8.InputDependencyGraphConsumer;
import com.android.tools.r8.origin.Origin;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DepsFileWriter implements InputDependencyGraphConsumer {

  private final Path dependentFile;
  private final String dependencyOutput;
  private final Set<Path> dependencies = new HashSet<>();

  public DepsFileWriter(Path dependentFile, String dependencyOutput) {
    this.dependentFile = dependentFile;
    this.dependencyOutput = dependencyOutput;
  }

  @Override
  public void accept(Origin dependent, Path dependency) {
    dependencies.add(dependency);
  }

  @Override
  public void finished() {
    List<Path> sorted = new ArrayList<>(dependencies);
    sorted.sort(Path::compareTo);
    Path output = Paths.get(dependencyOutput);
    try (Writer writer =
        Files.newBufferedWriter(
            output,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING)) {
      writer.write(escape(dependentFile.toString()));
      writer.write(":");
      for (Path path : sorted) {
        writer.write(" ");
        writer.write(escape(path.toString()));
      }
      writer.write("\n");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static String escape(String filepath) {
    return filepath.replace(" ", "\\ ");
  }
}
