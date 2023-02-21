// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NoVerticalClassMerging;
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
public class VirtualMethodWithConstantArgumentThroughSiblingInterfaceMethodTest extends TestBase {

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
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              // The A.m() and C.m() methods have been optimized.
              for (Class<?> clazz : new Class[] {A.class, C.class}) {
                ClassSubject aClassSubject = inspector.clazz(clazz);
                assertThat(aClassSubject, isPresent());
                MethodSubject aMethodSubject = aClassSubject.uniqueMethodWithOriginalName("m");
                assertThat(aMethodSubject, isPresent());
                assertTrue(aMethodSubject.streamInstructions().noneMatch(InstructionSubject::isIf));
              }
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A: Not null");
  }

  static class Main {

    public static void main(String[] args) {
      I i = System.currentTimeMillis() > 0 ? new B() : new C();
      i.m(new Object());
    }
  }

  @NoVerticalClassMerging
  interface I {

    void m(Object o);
  }

  @NoVerticalClassMerging
  static class A {

    public void m(Object o) {
      if (o != null) {
        System.out.println("A: Not null");
      } else {
        System.out.println("A: Null");
      }
    }
  }

  static class B extends A implements I {}

  static class C implements I {

    @Override
    public void m(Object o) {
      if (o != null) {
        System.out.println("C: Not null");
      } else {
        System.out.println("C: Null");
      }
    }
  }
}
