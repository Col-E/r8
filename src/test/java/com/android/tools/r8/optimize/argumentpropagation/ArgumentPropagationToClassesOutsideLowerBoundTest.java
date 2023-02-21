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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ArgumentPropagationToClassesOutsideLowerBoundTest extends TestBase {

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

              // TODO(b/190154391): B.m() is always called with non-null.
              MethodSubject bMethodSubject =
                  inspector.clazz(B.class).uniqueMethodWithOriginalName("m");
              assertThat(bMethodSubject, isPresent());
              assertTrue(
                  bMethodSubject
                      .streamInstructions()
                      .anyMatch(instruction -> instruction.isConstString("B: Not null")));
              assertTrue(
                  bMethodSubject
                      .streamInstructions()
                      .anyMatch(instruction -> instruction.isConstString("B: Null")));

              // TODO(b/190154391): C.m() is always called with null.
              MethodSubject cMethodSubject =
                  inspector.clazz(C.class).uniqueMethodWithOriginalName("m");
              assertThat(cMethodSubject, isPresent());
              assertTrue(
                  cMethodSubject
                      .streamInstructions()
                      .anyMatch(instruction -> instruction.isConstString("C: Not null")));
              assertTrue(
                  cMethodSubject
                      .streamInstructions()
                      .anyMatch(instruction -> instruction.isConstString("C: Null")));
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A: Not null", "A: Null");
  }

  static class Main {

    public static void main(String[] args) {
      A aOrB = System.currentTimeMillis() > 0 ? new A() : new B();
      aOrB.m(new Object());

      A aOrC = System.currentTimeMillis() > 0 ? new A() : new C();
      aOrC.m(null);
    }
  }

  static class A {

    void m(Object o) {
      if (o != null) {
        System.out.println("A: Not null");
      } else {
        System.out.println("A: Null");
      }
    }
  }

  @NoHorizontalClassMerging
  static class B extends A {

    void m(Object o) {
      if (o != null) {
        System.out.println("B: Not null");
      } else {
        System.out.println("B: Null");
      }
    }
  }

  @NoHorizontalClassMerging
  static class C extends A {

    void m(Object o) {
      if (o != null) {
        System.out.println("C: Not null");
      } else {
        System.out.println("C: Null");
      }
    }
  }
}
