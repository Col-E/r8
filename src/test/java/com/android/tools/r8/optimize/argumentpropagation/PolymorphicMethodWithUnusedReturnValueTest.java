// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PolymorphicMethodWithUnusedReturnValueTest extends TestBase {

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
        .enableNoHorizontalClassMergingAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        // TODO(b/173398086): uniqueMethodWithName() does not work with proto changes.
        .addDontObfuscate()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(
            inspector -> {
              // The test() methods have been changed to have return type void.
              for (Class<?> clazz : new Class<?>[] {A.class, B.class}) {
                MethodSubject testMethodSubject =
                    inspector.clazz(clazz).uniqueMethodWithOriginalName("m");
                assertThat(testMethodSubject, isPresent());
                assertTrue(testMethodSubject.getProgramMethod().getReturnType().isVoidType());
              }

              // The class ReturnType has been removed by tree shaking.
              assertThat(inspector.clazz(ReturnType.class), isAbsent());
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A.m()");
  }

  static class Main {

    public static void main(String[] args) {
      A a = System.currentTimeMillis() >= 0 ? new A() : new B();
      a.m();
    }
  }

  @NoHorizontalClassMerging
  @NoVerticalClassMerging
  static class A {

    ReturnType m() {
      System.out.println("A.m()");
      return new ReturnType();
    }
  }

  static class B extends A {

    @Override
    ReturnType m() {
      System.out.println("B.m()");
      return new ReturnType();
    }
  }

  @NoHorizontalClassMerging
  static class ReturnType {}
}
