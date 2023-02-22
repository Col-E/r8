// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.assumevalues;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

class TestClass {
  private static final boolean HAS_R8 = Boolean.parseBoolean("false");

  public static void main(String... args) {
    if (HAS_R8) {
      System.out.println("R8");
    } else {
      System.out.println("No R8");
    }
  }
}

@RunWith(Parameterized.class)
public class DeadFieldAfterAssumevaluesTest extends TestBase {
  private static final Class<?> MAIN = TestClass.class;
  private static final String EXPECTED_OUTPUT = StringUtils.lines("R8");
  private static final String RULES = StringUtils.lines(
      "-assumevalues class **.TestClass {",
      "  static boolean HAS_R8 return true;",
      "}",
      "-assumenosideeffects class java.lang.Boolean {",
      "  static boolean parseBoolean(java.lang.String);",
      "}"
  );

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Parameter(0)
  public TestParameters parameters;

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(MAIN)
        .addKeepMainRule(MAIN)
        .addKeepRules(RULES)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(EXPECTED_OUTPUT)
        .inspect(this::inspect);
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject main = inspector.clazz(MAIN);
    assertThat(main, isPresent());

    MethodSubject mainMethod = main.mainMethod();
    assertThat(mainMethod, isPresent());
    // After applying -assumevalues, no more branching in the main method.
    assertTrue(mainMethod.streamInstructions()
        .noneMatch(i -> i.isIf() || i.isIfEqz() || i.isIfNez()));

    FieldSubject hasR8 = main.uniqueFieldWithOriginalName("HAS_R8");
    assertThat(hasR8, not(isPresent()));

    MethodSubject clinit = main.clinit();
    assertThat(clinit, not(isPresent()));
  }
}
