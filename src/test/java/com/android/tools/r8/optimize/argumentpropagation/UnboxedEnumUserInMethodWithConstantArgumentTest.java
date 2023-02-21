// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.MatcherAssert.assertThat;

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
public class UnboxedEnumUserInMethodWithConstantArgumentTest extends TestBase {

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
                      .noneMatch(InstructionSubject::isConstNumber));

              MethodSubject testMethodSubject =
                  mainClassSubject.uniqueMethodWithOriginalName("test");
              assertThat(testMethodSubject, isPresent());
              assertEquals(0, testMethodSubject.getProgramMethod().getReference().getArity());
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A");
  }

  static class Main {

    public static void main(String[] args) {
      test(true);
    }

    @NeverInline
    static void test(boolean alwaysTrue) {
      if (alwaysTrue) {
        // Failure to reprocess this method after the unboxing of MyEnum will lead to runtime
        // errors.
        MyEnum myEnum = System.currentTimeMillis() > 0 ? MyEnum.A : MyEnum.B;
        System.out.println(myEnum.name());
      }
    }
  }

  enum MyEnum {
    A,
    B
  }
}
