// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import com.android.tools.r8.D8Command.Builder;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.errors.InternalCompilerError;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

public class D8IncrementalRunExamplesJava9Test extends RunExamplesJava9Test<D8Command.Builder> {

  class D8TestRunner extends TestRunner<D8TestRunner> {

    D8TestRunner(String testName, String packageName, String mainClass) {
      super(testName, packageName, mainClass);
    }

    @Override
    D8TestRunner withMinApiLevel(int minApiLevel) {
      return withBuilderTransformation(builder -> builder.setMinApiLevel(minApiLevel));
    }

    @Override
    void build(Path inputFile, Path out) throws Throwable {
      List<String> dexFiles = compileIncremental(inputFile);
      assert !dexFiles.isEmpty();
      mergeDexFiles(dexFiles, out);
    }

    private List<String> compileIncremental(Path inputFile) throws Throwable {
      Builder builder = D8Command.builder();
      for (UnaryOperator<Builder> transformation : builderTransformations) {
        builder = transformation.apply(builder);
      }
      // Root to incremental output
      Path incrementalOutput = temp.getRoot().toPath().resolve("incremental");
      builder
          .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
          .addProgramFiles(inputFile)
          .setOutput(incrementalOutput, OutputMode.DexFilePerClassFile);
      ToolHelper.runD8(builder, this::combinedOptionConsumer);
      return collectDexFiles(incrementalOutput);
    }

    private void mergeDexFiles(List<String> dexFiles, Path out) throws Throwable {
      Builder builder = D8Command.builder();
      builder.addProgramFiles(
          dexFiles.stream().map(str -> Paths.get(str)).collect(Collectors.toList()));
      for (UnaryOperator<Builder> transformation : builderTransformations) {
        builder = transformation.apply(builder);
      }
      builder.setOutput(out, OutputMode.DexIndexed);
      try {
        AndroidApp app = ToolHelper.runD8(builder, this::combinedOptionConsumer);
        assert app.getDexProgramResourcesForTesting().size() == 1;
      } catch (Unimplemented | CompilationError | InternalCompilerError re) {
        throw re;
      } catch (RuntimeException re) {
        throw re.getCause() == null ? re : re.getCause();
      }
    }

    private List<String> collectDexFiles(Path incrementalOutput) {
      List<String> result = new ArrayList<>();
      collectDexFiles(incrementalOutput, result);
      Collections.sort(result);
      return result;
    }

    private void collectDexFiles(Path dir, List<String> result) {
      if (Files.exists(dir)) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
          for (Path entry : stream) {
            if (Files.isDirectory(entry)) {
              collectDexFiles(entry, result);
            } else {
              result.add(entry.toString());
            }
          }
        } catch (IOException x) {
          throw new AssertionError(x);
        }
      }
    }

    @Override
    D8TestRunner self() {
      return this;
    }
  }

  @Override
  D8TestRunner test(String testName, String packageName, String mainClass) {
    return new D8TestRunner(testName, packageName, mainClass);
  }
}
