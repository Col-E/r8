// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.java_language.pattern_matching_for_instenceof;

import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfRuntime;
import com.android.tools.r8.examples.jdk16.PatternMatchingForInstenceof;
import com.android.tools.r8.utils.InternalOptions.TestingOptions;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PattternMatchingForInstanceOfTest extends TestBase {

  @Parameter public TestParameters parameters;

  private static List<String> EXPECTED = ImmutableList.of("Hello, world!");

  private static final Path JAR = PatternMatchingForInstenceof.jar();
  private static final String MAIN = PatternMatchingForInstenceof.Main.typeName();

  @Parameters(name = "{0}")
  public static List<Object[]> data() {
    // TODO(b/174431251): This should be replaced with .withCfRuntimes(start = jdk16).
    return buildParameters(
        getTestParameters()
            .withCustomRuntime(CfRuntime.getCheckedInJdk16())
            .withDexRuntimes()
            .withAllApiLevelsAlsoForCf()
            .build());
  }

  @Test
  public void testD8AndJvm() throws Exception {
    if (parameters.isCfRuntime()) {
      testForJvm()
          .addRunClasspathFiles(JAR)
          .enablePreview()
          .run(parameters.getRuntime(), MAIN)
          .assertSuccessWithOutputLines(EXPECTED);
    }
    testForD8(parameters.getBackend())
        .addProgramFiles(JAR)
        .setMinApi(parameters.getApiLevel())
        .addOptionsModification(TestingOptions::allowExperimentClassFileVersion)
        .compile()
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    R8TestBuilder<?> builder =
        testForR8(parameters.getBackend())
            .addProgramFiles(JAR)
            .setMinApi(parameters.getApiLevel())
            .addKeepMainRule(MAIN)
            .addOptionsModification(TestingOptions::allowExperimentClassFileVersion);
    if (parameters.getBackend().isDex()) {
      builder.run(parameters.getRuntime(), MAIN).assertSuccessWithOutputLines(EXPECTED);
    } else {
      testForJvm()
          .addRunClasspathFiles(builder.compile().writeToZip())
          .enablePreview()
          .run(parameters.getRuntime(), MAIN)
          .assertSuccessWithOutputLines(EXPECTED);
    }
  }
}
