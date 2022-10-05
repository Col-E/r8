// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ReturnTypeStrengtheningTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        // TODO(b/173398086): uniqueMethodWithName() does not work with argument changes.
        .addDontObfuscate()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(
            inspector -> {
              ClassSubject mainClassSubject = inspector.clazz(Main.class);
              assertThat(mainClassSubject, isPresent());

              ClassSubject aClassSubject = inspector.clazz(A.class);
              assertThat(aClassSubject, isPresent());

              // Return type of get() should be strengthened to A.
              MethodSubject getMethodSubject = mainClassSubject.uniqueMethodWithOriginalName("get");
              assertThat(getMethodSubject, isPresent());
              assertEquals(
                  aClassSubject.getFinalName(),
                  getMethodSubject.getProgramMethod().getReturnType().getTypeName());

              // Method consume(I) should be rewritten to consume(A).
              MethodSubject testBMethodSubject =
                  mainClassSubject.uniqueMethodWithOriginalName("consume");
              assertThat(testBMethodSubject, isPresent());
              assertEquals(
                  aClassSubject.getFinalName(),
                  testBMethodSubject.getProgramMethod().getParameter(0).getTypeName());

              // There should be no casts in the application.
              for (FoundClassSubject classSubject : inspector.allClasses()) {
                for (FoundMethodSubject methodSubject : classSubject.allMethods()) {
                  assertTrue(
                      methodSubject
                          .streamInstructions()
                          .noneMatch(InstructionSubject::isCheckCast));
                }
              }
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A");
  }

  static class Main {

    public static void main(String[] args) {
      consume(get());
    }

    @NeverInline
    static I get() {
      return new A();
    }

    @NeverInline
    static void consume(I i) {
      i.m();
    }
  }

  @NoVerticalClassMerging
  interface I {

    void m();
  }

  static class A implements I {

    @Override
    public void m() {
      System.out.println("A");
    }
  }
}
