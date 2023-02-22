// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.initializedclasses;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
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
public class InitializedClassesOnNormalExitAnalysisTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    String expectedOutput = StringUtils.lines("A.<clinit>()", "A.foo()");
    testForR8(parameters.getBackend())
        .addInnerClasses(InitializedClassesOnNormalExitAnalysisTest.class)
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(this::verifyInlineableHasBeenInlined)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(expectedOutput);
  }

  private void verifyInlineableHasBeenInlined(CodeInspector inspector) {
    // Verify that main() only invokes loadA() and println().
    {
      ClassSubject classSubject = inspector.clazz(TestClass.class);
      assertThat(classSubject, isPresent());

      MethodSubject methodSubject = classSubject.mainMethod();
      assertThat(methodSubject, isPresent());

      assertEquals(
          2, methodSubject.streamInstructions().filter(InstructionSubject::isInvoke).count());
    }

    // Verify absence of inlineable().
    {
      ClassSubject classSubject = inspector.clazz(A.class);
      assertThat(classSubject, isPresent());

      MethodSubject methodSubject = classSubject.uniqueMethodWithOriginalName("inlineable");
      assertThat(methodSubject, not(isPresent()));
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      loadA();
      A.inlineable();
    }

    @NeverInline
    static void loadA() {
      A.load();
    }
  }

  static class A {

    static {
      System.out.println("A.<clinit>()");
    }

    static void load() {}

    static void inlineable() {
      System.out.println("A.foo()");
    }
  }
}
