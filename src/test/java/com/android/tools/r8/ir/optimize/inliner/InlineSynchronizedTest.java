// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class InlineSynchronizedTest extends TestBase {

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private final TestParameters parameters;

  public InlineSynchronizedTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(InlineSynchronizedTest.class)
        .addKeepMainRule(TestClass.class)
        .enableNeverClassInliningAnnotations()
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject testClassSubject = inspector.clazz(TestClass.class);
              assertThat(testClassSubject, isPresent());

              // Check that there is a single monitor-enter instruction (for the normal inlining
              // case).
              assertEquals(
                  1,
                  testClassSubject
                      .mainMethod()
                      .streamInstructions()
                      .filter(InstructionSubject::isMonitorEnter)
                      .count());

              // Check that there are two monitor-exit instructions (for the normal inlining case,
              // one for the normal exit and one for the exceptional exit).
              assertEquals(
                  2,
                  testClassSubject
                      .mainMethod()
                      .streamInstructions()
                      .filter(InstructionSubject::isMonitorExit)
                      .count());

              ClassSubject aClassSubject = inspector.clazz(A.class);
              assertThat(aClassSubject, isPresent());
              assertThat(
                  aClassSubject.uniqueMethodWithOriginalName("normalInlinedSynchronized"),
                  not(isPresent()));

              ClassSubject bClassSubject = inspector.clazz(B.class);
              assertThat(bClassSubject, not(isPresent()));
            })
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines(
            "A::normalInlinedSynchronized", "B::classInlinedSynchronized");
  }

  static class TestClass {

    public static void main(String[] args) {
      // Test normal inlining.
      new A().normalInlinedSynchronized();

      // Test class-inlining.
      new B().classInlinedSynchronized();
    }
  }

  @NeverClassInline
  static class A {

    synchronized void normalInlinedSynchronized() {
      System.out.println("A::normalInlinedSynchronized");
    }
  }

  static class B {

    @NeverInline
    synchronized void classInlinedSynchronized() {
      System.out.println("B::classInlinedSynchronized");
    }
  }
}
