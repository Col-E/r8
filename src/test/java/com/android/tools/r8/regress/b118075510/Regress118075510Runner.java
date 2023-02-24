// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.regress.b118075510;

import com.android.tools.r8.AsmTestBase;
import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class Regress118075510Runner extends AsmTestBase {

  private static final Class<?> CLASS = Regress118075510Test.class;
  private static final String EXPECTED = StringUtils.lines("0", "0");

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
        .addTestClasspath()
        .run(parameters.getRuntime(), CLASS)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    D8TestCompileResult d8Result =
        testForD8().addProgramClasses(CLASS).setMinApi(AndroidApiLevel.M).release().compile();

    CodeInspector inspector = d8Result.inspector();
    checkMethodContainsLongSignum(inspector, "fooNoTryCatch");
    checkMethodContainsLongSignum(inspector, "fooWithTryCatch");
    // Check the program runs on ART/Dalvik
    d8Result.run(parameters.getRuntime(), CLASS).assertSuccessWithOutput(EXPECTED);
    // Check the program can be dex2oat compiled to arm64. This will diverge without the fixup.
    d8Result.runDex2Oat(parameters.getRuntime()).assertSuccess();
  }

  private void checkMethodContainsLongSignum(CodeInspector inspector, String methodName)
      throws NoSuchMethodException {
    MethodSubject method = inspector.method(CLASS.getMethod(methodName, long.class, long.class));
    Assert.assertTrue(
        "Did not contain Long.signum workaround in "
            + methodName
            + ":\n"
            + method.getMethod().codeToString(),
        method
            .streamInstructions()
            .anyMatch(
                i ->
                    i.isInvoke() && i.getMethod().qualifiedName().equals("java.lang.Long.signum")));
  }
}
