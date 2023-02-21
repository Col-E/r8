// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.HorizontallyMergedClassesInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.android.tools.r8.utils.codeinspector.VerticallyMergedClassesInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class UpwardsInterfacePropagationToUnrelatedMethodTest extends TestBase {

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
        .addHorizontallyMergedClassesInspector(
            HorizontallyMergedClassesInspector::assertNoClassesMerged)
        .addVerticallyMergedClassesInspector(
            VerticallyMergedClassesInspector::assertNoClassesMerged)
        .enableNoHorizontalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              MethodSubject aMethodSubject =
                  inspector.clazz(A.class).uniqueMethodWithOriginalName("m");
              assertThat(aMethodSubject, isPresent());
              assertTrue(
                  aMethodSubject
                      .streamInstructions()
                      .anyMatch(instruction -> instruction.isConstString("A: Not null")));
              assertTrue(
                  aMethodSubject
                      .streamInstructions()
                      .anyMatch(instruction -> instruction.isConstString("A: Null")));

              MethodSubject b2MethodSubject =
                  inspector.clazz(B2.class).uniqueMethodWithOriginalName("m");
              assertThat(b2MethodSubject, isPresent());
              assertTrue(
                  b2MethodSubject
                      .streamInstructions()
                      .anyMatch(instruction -> instruction.isConstString("B2: Not null")));
              assertTrue(
                  b2MethodSubject
                      .streamInstructions()
                      .noneMatch(instruction -> instruction.isConstString("B2: Null")));

              MethodSubject b3MethodSubject =
                  inspector.clazz(B3.class).uniqueMethodWithOriginalName("m");
              assertThat(b3MethodSubject, isPresent());
              assertTrue(
                  b3MethodSubject
                      .streamInstructions()
                      .noneMatch(instruction -> instruction.isConstString("B3: Not null")));
              assertTrue(
                  b3MethodSubject
                      .streamInstructions()
                      .anyMatch(instruction -> instruction.isConstString("B3: Null")));
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A: Not null", "A: Null");
  }

  static class Main {

    public static void main(String[] args) {
      // Call A.m() or B2.m() with a non-null object.
      A a = System.currentTimeMillis() >= 0 ? new B() : new B2();
      a.m(new Object());

      // Call A.m() or B3.m() with a null object.
      I i = System.currentTimeMillis() >= 0 ? new B() : new B3();
      i.m(null);
    }
  }

  interface I {

    void m(Object o);
  }

  static class A {

    public void m(Object o) {
      if (o != null) {
        System.out.println("A: Not null");
      } else {
        System.out.println("A: Null");
      }
    }
  }

  @NoHorizontalClassMerging
  static class B extends A implements I {}

  @NoHorizontalClassMerging
  static class B2 extends A {

    @Override
    public void m(Object o) {
      if (o != null) {
        System.out.println("B2: Not null");
      } else {
        System.out.println("B2: Null");
      }
    }
  }

  static class B3 implements I {

    @Override
    public void m(Object o) {
      if (o != null) {
        System.out.println("B3: Not null");
      } else {
        System.out.println("B3: Null");
      }
    }
  }
}
