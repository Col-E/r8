// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf;

import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DebugInfoTestRunner extends TestBase {

  private static final Class<?> CLASS = DebugInfoTest.class;
  private static final String EXPECTED = "";

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
        .addProgramClasses(CLASS)
        .run(parameters.getRuntime(), CLASS)
        .assertSuccessWithEmptyOutput();
  }

  @Test
  public void test() throws Exception {
    // Compile the input with R8 and run.
    Path out = temp.getRoot().toPath().resolve("out.zip");
    builder()
        .addProgramClasses(CLASS)
        .compile()
        .writeToZip(out)
        .run(parameters.getRuntime(), CLASS)
        .assertSuccessWithEmptyOutput();

    if (parameters.isCfRuntime()) {
      // If first compilation was to CF, then compile and run it again.
      builder()
          .addProgramFiles(out)
          .run(parameters.getRuntime(), CLASS)
          .assertSuccessWithEmptyOutput();
    }
  }

  private R8FullTestBuilder builder() {
    return testForR8(parameters.getBackend())
        .debug()
        .noTreeShaking()
        .addDontObfuscate()
        .addOptionsModification(o -> o.invalidDebugInfoFatal = true)
        .setMinApi(parameters);
  }
}
