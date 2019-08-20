// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.examples.newarray;

import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class NewArrayTestRunner extends TestBase {

  static final Class<?> CLASS = NewArray.class;

  private final TestParameters parameters;
  private final CompilationMode mode;

  private static String referenceOut;

  @Parameterized.Parameters(name = "{0}, {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimes().withAllApiLevels().build(), CompilationMode.values());
  }

  public NewArrayTestRunner(TestParameters parameters, CompilationMode mode) {
    this.parameters = parameters;
    this.mode = mode;
  }

  @BeforeClass
  public static void runReference() throws Exception {
    referenceOut =
        testForJvm(getStaticTemp())
            .addProgramClassesAndInnerClasses(CLASS)
            .run(TestRuntime.getDefaultJavaRuntime(), CLASS)
            .assertSuccess()
            .getStdOut();
  }

  @Test
  public void testD8() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForD8()
        .addProgramClassesAndInnerClasses(CLASS)
        .setMinApi(parameters.getApiLevel())
        .setMode(mode)
        .run(parameters.getRuntime(), CLASS)
        .assertSuccessWithOutput(referenceOut);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClassesAndInnerClasses(CLASS)
        .addKeepMainRule(CLASS)
        .setMinApi(parameters.getApiLevel())
        .setMode(mode)
        .run(parameters.getRuntime(), CLASS)
        .assertSuccessWithOutput(referenceOut);
  }
}
