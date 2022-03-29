// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.java_language.pattern_matching_for_instenceof;

import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.examples.jdk17.PatternMatchingForInstanceof;
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

  private static final Path JAR = PatternMatchingForInstanceof.jar();
  private static final String MAIN = PatternMatchingForInstanceof.Main.typeName();

  @Parameters(name = "{0}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters()
            .withCfRuntimesStartingFromIncluding(CfVm.JDK17)
            .withDexRuntimes()
            .withAllApiLevelsAlsoForCf()
            .build());
  }

  @Test
  public void testD8AndJvm() throws Exception {
    if (parameters.isCfRuntime()) {
      testForJvm()
          .addRunClasspathFiles(JAR)
          .run(parameters.getRuntime(), MAIN)
          .assertSuccessWithOutputLines(EXPECTED);
    }
    testForD8(parameters.getBackend())
        .addProgramFiles(JAR)
        .setMinApi(parameters.getApiLevel())
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
            .addKeepMainRule(MAIN);
    if (parameters.getBackend().isDex()) {
      builder.run(parameters.getRuntime(), MAIN).assertSuccessWithOutputLines(EXPECTED);
    } else {
      testForJvm()
          .addRunClasspathFiles(builder.compile().writeToZip())
          .run(parameters.getRuntime(), MAIN)
          .assertSuccessWithOutputLines(EXPECTED);
    }
  }
}
