// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MixedArgumentRemovalAndEnumUnboxingTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection parameters() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addEnumUnboxingInspector(inspector -> inspector.assertUnboxed(MyEnum.class))
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject mainClassSubject = inspector.clazz(Main.class);
              assertThat(mainClassSubject, isPresent());

              MethodSubject mainMethodSubject = mainClassSubject.mainMethod();
              assertThat(mainMethodSubject, isPresent());
              assertTrue(
                  mainMethodSubject
                      .streamInstructions()
                      .noneMatch(InstructionSubject::isConstNull));

              MethodSubject testMethodSubject =
                  mainClassSubject.uniqueMethodWithOriginalName("test");
              assertThat(testMethodSubject, isPresent());
              assertEquals(2, testMethodSubject.getProgramMethod().getReference().getArity());
              assertEquals(
                  "int", testMethodSubject.getProgramMethod().getParameter(0).getTypeName());
              assertEquals(
                  "int", testMethodSubject.getProgramMethod().getParameter(1).getTypeName());
              assertTrue(
                  testMethodSubject.streamInstructions().noneMatch(InstructionSubject::isIf));
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A", "B");
  }

  static class Main {

    public static void main(String[] args) {
      MyEnum alwaysA = System.currentTimeMillis() >= 1 ? MyEnum.A : MyEnum.B;
      MyEnum alwaysB = System.currentTimeMillis() >= 1 ? MyEnum.B : MyEnum.A;
      test(null, alwaysA, null, alwaysB);
    }

    @NeverInline
    static void test(Main alwaysNull, MyEnum alwaysA, Main alsoAlwaysNull, MyEnum alwaysB) {
      if (alwaysNull == null && alsoAlwaysNull == null) {
        System.out.println(alwaysA.name());
        System.out.println(alwaysB.name());
      }
    }
  }

  enum MyEnum {
    A,
    B
  }
}
