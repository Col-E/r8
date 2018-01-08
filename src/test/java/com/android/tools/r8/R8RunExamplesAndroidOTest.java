// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.junit.Test;

public class R8RunExamplesAndroidOTest extends RunExamplesAndroidOTest<R8Command.Builder> {

  private static Map<DexVm.Version, List<String>> alsoFailsOn =
      ImmutableMap.<DexVm.Version, List<String>>builder()
          .put(Version.V4_0_4,
              ImmutableList.of(
              "invokecustom-with-shrinking"
              ))
          .put(Version.V4_4_4,
              ImmutableList.of(
              "invokecustom-with-shrinking"
              ))
          .put(Version.V5_1_1,
              ImmutableList.of(
              "invokecustom-with-shrinking"
              ))
          .put(Version.V6_0_1,
              ImmutableList.of(
              "invokecustom-with-shrinking"
              ))
          .put(Version.V7_0_0,
              ImmutableList.of(
                  "invokecustom-with-shrinking"
              ))
          .put(Version.DEFAULT,
              ImmutableList.of(
              ))
          .build();

  @Test
  public void invokeCustomWithShrinking() throws Throwable {
    test("invokecustom-with-shrinking", "invokecustom", "InvokeCustom")
        .withMinApiLevel(AndroidApiLevel.O.getLevel())
        .withBuilderTransformation(builder ->
            builder.addProguardConfigurationFiles(
                Paths.get(ToolHelper.EXAMPLES_ANDROID_O_DIR, "invokecustom/keep-rules.txt")))
        .run();
  }

  class R8TestRunner extends TestRunner<R8TestRunner> {

    R8TestRunner(String testName, String packageName, String mainClass) {
      super(testName, packageName, mainClass);
    }

    @Override
    R8TestRunner withMinApiLevel(int minApiLevel) {
      return withBuilderTransformation(builder -> builder.setMinApiLevel(minApiLevel));
    }

    @Override
    void build(Path inputFile, Path out, OutputMode mode) throws Throwable {
      R8Command.Builder builder = R8Command.builder().setOutput(out, mode);
      for (UnaryOperator<R8Command.Builder> transformation : builderTransformations) {
        builder = transformation.apply(builder);
      }
      builder.addLibraryFiles(Paths.get(ToolHelper.getAndroidJar(
          androidJarVersion == null ? builder.getMinApiLevel() : androidJarVersion)));
      R8Command command = builder.addProgramFiles(inputFile).build();
      ToolHelper.runR8(command, this::combinedOptionConsumer);
    }

    @Override
    R8TestRunner self() {
      return this;
    }
  }

  @Override
  R8TestRunner test(String testName, String packageName, String mainClass) {
    return new R8TestRunner(testName, packageName, mainClass);
  }

  @Override
  boolean expectedToFail(String name) {
    return super.expectedToFail(name) || failsOn(alsoFailsOn, name);
  }
}
