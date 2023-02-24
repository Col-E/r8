// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethod;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNot.not;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InliningWithClassInitializerTest extends TestBase {

  private static final String EXPECTED_OUTPUT =
      StringUtils.lines("In A.<clinit>()", "In B.<clinit>()", "In B.other()");

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addTestClasspath()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    CodeInspector inspector =
        testForR8(parameters.getBackend())
            .addInnerClasses(InliningWithClassInitializerTest.class)
            .addKeepMainRule(TestClass.class)
            .enableInliningAnnotations()
            .enableNoVerticalClassMergingAnnotations()
            .setMinApi(parameters)
            .run(parameters.getRuntime(), TestClass.class)
            .assertSuccessWithOutput(EXPECTED_OUTPUT)
            .inspector();

    ClassSubject classA = inspector.clazz(A.class);
    assertThat(classA, isPresent());

    ClassSubject classB = inspector.clazz(B.class);
    assertThat(classB, isPresent());

    MethodSubject inlineableMethod = classB.uniqueMethodWithOriginalName("inlineable");
    assertThat(inlineableMethod, not(isPresent()));

    MethodSubject otherMethod = classB.uniqueMethodWithOriginalName("other");
    assertThat(otherMethod, isPresent());

    MethodSubject mainMethod = inspector.clazz(TestClass.class).mainMethod();
    assertThat(mainMethod, invokesMethod(otherMethod));
  }

  static class TestClass {

    public static void main(String[] args) {
      // Should be inlined since the call to `B.other()` will ensure that the static initalizer in
      // A will continue to be executed even after inlining.
      B.inlineable();
    }
  }

  @NoVerticalClassMerging
  static class A {

    static {
      System.out.println("In A.<clinit>()");
    }
  }

  static class B extends A {

    static {
      System.out.println("In B.<clinit>()");
    }

    static void inlineable() {
      other();
    }

    @NeverInline
    static void other() {
      System.out.println("In B.other()");
    }
  }
}
