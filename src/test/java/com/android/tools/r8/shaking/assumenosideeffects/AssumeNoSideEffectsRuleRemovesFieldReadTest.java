// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.assumenosideeffects;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AssumeNoSideEffectsRuleRemovesFieldReadTest extends TestBase {

  private static final String OUT_SIGNATURE = "java.io.PrintStream java.lang.System.out";

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(AssumeNoSideEffectsRuleRemovesFieldReadTest.class)
        .addKeepMainRule(TestClass.class)
        .addKeepRules(
            "-assumenosideeffects class " + A.class.getTypeName() + " {",
            "  static boolean booleanField return true;",
            "  static int intField return 0..42;",
            "}")
        .setMinApi(parameters)
        .compile()
        .inspect(this::verifyFieldIsAbsent)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  private void verifyFieldIsAbsent(CodeInspector inspector) {
    ClassSubject testClassSubject = inspector.clazz(TestClass.class);
    assertThat(testClassSubject, isPresent());

    MethodSubject methodSubject = testClassSubject.mainMethod();
    assertThat(methodSubject, isPresent());
    assertTrue(
        methodSubject
            .streamInstructions()
            .filter(InstructionSubject::isStaticGet)
            .allMatch(staticGet -> staticGet.getField().toSourceString().equals(OUT_SIGNATURE)));

    ClassSubject otherClassSubject = inspector.clazz(A.class);
    assertThat(otherClassSubject, not(isPresent()));
  }

  static class TestClass {

    public static void main(String[] args) {
      if (A.booleanField) {
        System.out.print("Hello");
      }
      if (A.intField >= 0) {
        System.out.println(" world!");
      }
    }
  }

  static class A {

    static {
      System.out.println("A.<clinit>()");
    }

    static boolean booleanField = false;
    static int intField = -1;
  }
}
