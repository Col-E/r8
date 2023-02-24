// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.nestaccesscontrol;

import static com.android.tools.r8.utils.FileUtils.JAR_EXTENSION;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NestLambdaJava17Test extends TestBase {

  private static final Path JDK17_JAR =
      Paths.get(ToolHelper.TESTS_BUILD_DIR, "examplesJava17").resolve("nest" + JAR_EXTENSION);
  private static final String MAIN = "nest.NestLambda";
  private static final String EXPECTED_RESULT =
      StringUtils.lines("printed: inner", "printed from itf: here");

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK17)
        .withDexRuntimes()
        .withAllApiLevelsAlsoForCf()
        .build();
  }

  @Test
  public void testReference() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramFiles(JDK17_JAR)
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testJavaD8() throws Exception {
    testForD8(parameters.getBackend())
        .addProgramFiles(JDK17_JAR)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .addProgramFiles(JDK17_JAR)
        .applyIf(
            parameters.isCfRuntime(),
            // Alternatively we need to pass Jdk17 as library.
            b -> b.addKeepRules("-dontwarn java.lang.invoke.StringConcatFactory"))
        .setMinApi(parameters)
        .addKeepMainRule(MAIN)
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }
}
