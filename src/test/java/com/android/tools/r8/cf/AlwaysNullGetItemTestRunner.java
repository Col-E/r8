// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class AlwaysNullGetItemTestRunner extends TestBase {

  private static final Class<?> CLASS = AlwaysNullGetItemTest.class;

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public AlwaysNullGetItemTestRunner(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    ProcessResult runInput =
        ToolHelper.runJava(ToolHelper.getClassPathForTests(), CLASS.getCanonicalName());
    assertEquals(0, runInput.exitCode);
    testForR8(parameters.getBackend())
        .addProgramClassesAndInnerClasses(CLASS)
        .debug()
        .addDontObfuscate()
        .noTreeShaking()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), CLASS)
        .assertSuccessWithOutputLines(NullPointerException.class.getSimpleName());
  }

  @Test
  public void testNoCheckCast() throws Exception {
    // Test that JVM accepts javac output when method calls have been replaced by ACONST_NULL.
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClassFileData(AlwaysNullGetItemDump.dump())
        .run(parameters.getRuntime(), CLASS)
        .assertSuccessWithOutputLines(NullPointerException.class.getSimpleName());
  }
}
