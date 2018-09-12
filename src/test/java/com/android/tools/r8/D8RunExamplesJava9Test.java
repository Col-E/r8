// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import com.android.tools.r8.utils.AndroidApiLevel;
import java.nio.file.Path;
import java.util.function.UnaryOperator;

public class D8RunExamplesJava9Test extends RunExamplesJava9Test<D8Command.Builder> {

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
      D8Command.Builder builder = D8Command.builder();
      for (UnaryOperator<D8Command.Builder> transformation : builderTransformations) {
        builder = transformation.apply(builder);
      }
      // TODO(mikaelpeltier) Add new android.jar build from aosp and use it
      builder
          .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
          .addProgramFiles(inputFile)
          .setOutput(out, OutputMode.DexIndexed);
      ToolHelper.runD8(builder, this::combinedOptionConsumer);
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
