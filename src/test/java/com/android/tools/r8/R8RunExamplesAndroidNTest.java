// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import com.android.tools.r8.origin.Origin;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.function.UnaryOperator;

public class R8RunExamplesAndroidNTest extends RunExamplesAndroidNTest<R8Command.Builder> {

  class R8TestRunner extends TestRunner {

    R8TestRunner(String testName, String packageName, String mainClass) {
      super(testName, packageName, mainClass);
    }

    @Override
    TestRunner withMinApiLevel(int minApiLevel) {
      return withBuilderTransformation(builder -> builder.setMinApiLevel(minApiLevel));
    }

    @Override
    TestRunner withKeepAll() {
      return withBuilderTransformation(builder ->
          builder
              .setDisableTreeShaking(true)
              .setDisableMinification(true)
              .addProguardConfiguration(ImmutableList.of("-keepattributes *"), Origin.unknown()));
    }

    @Override
    void build(Path inputFile, Path out) throws Throwable {
      R8Command.Builder builder = R8Command.builder();
      for (UnaryOperator<R8Command.Builder> transformation : builderTransformations) {
        builder = transformation.apply(builder);
      }
      R8Command command =
          builder.addProgramFiles(inputFile).setOutput(out, OutputMode.DexIndexed).build();
      ToolHelper.runR8(command, this::combinedOptionConsumer);
    }
  }

  @Override
  TestRunner test(String testName, String packageName, String mainClass) {
    return new R8TestRunner(testName, packageName, mainClass);
  }
}
