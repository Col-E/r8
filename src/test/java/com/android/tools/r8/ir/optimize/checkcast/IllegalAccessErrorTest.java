// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.checkcast;

import com.android.tools.r8.AsmTestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

class NonAccessible {
  // because it's package-private
}

@RunWith(Parameterized.class)
public class IllegalAccessErrorTest extends AsmTestBase {

  private static final String EXPECTED_OUTPUT = "null";
  private static final String MAIN = "Test";

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClasses(NonAccessible.class)
        .addProgramClassFileData(IllegalAccessErrorTestDump.dump())
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutputLines(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(NonAccessible.class)
        .addProgramClassFileData(IllegalAccessErrorTestDump.dump())
        .addKeepMainRule(MAIN)
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), MAIN)
        .applyIf(
            parameters.isCfRuntime(),
            runResult -> runResult.assertSuccessWithOutputLines(EXPECTED_OUTPUT),
            runResult ->
                runResult.assertSuccessWithOutputLines(IllegalAccessErrorTestDump.MESSAGE));
  }
}
