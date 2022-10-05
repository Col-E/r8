// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.accessesField;
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

@RunWith(Parameterized.class)
public class InliningFromCurrentClassTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public InliningFromCurrentClassTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    String expectedOutput =
        StringUtils.lines(
            "In A.<clinit>()",
            "In B.<clinit>()",
            "In A.inlineable1()",
            "In B.inlineable2()",
            "In C.<clinit>()",
            "In C.inlineableWithInitClass()");

    testForJvm().addTestClasspath().run(TestClass.class).assertSuccessWithOutput(expectedOutput);

    CodeInspector inspector =
        testForR8(parameters.getBackend())
            .addInnerClasses(InliningFromCurrentClassTest.class)
            .addKeepMainRule(TestClass.class)
            .enableInliningAnnotations()
            .enableNoVerticalClassMergingAnnotations()
            .setMinApi(parameters.getApiLevel())
            .run(parameters.getRuntime(), TestClass.class)
            .assertSuccessWithOutput(expectedOutput)
            .inspector();

    ClassSubject classA = inspector.clazz(A.class);
    assertThat(classA, isPresent());

    ClassSubject classB = inspector.clazz(B.class);
    assertThat(classB, isPresent());

    ClassSubject classC = inspector.clazz(C.class);
    assertThat(classC, isPresent());

    MethodSubject testMethod = classB.uniqueMethodWithOriginalName("test");
    assertThat(testMethod, isPresent());

    MethodSubject inlineable1Method = classA.uniqueMethodWithOriginalName("inlineable1");
    assertThat(inlineable1Method, not(isPresent()));

    MethodSubject inlineable2Method = classB.uniqueMethodWithOriginalName("inlineable2");
    assertThat(inlineable2Method, not(isPresent()));

    MethodSubject inlineableWithInitClassMethod =
        classC.uniqueMethodWithOriginalName("inlineableWithInitClass");
    assertThat(inlineableWithInitClassMethod, not(isPresent()));
    assertThat(testMethod, accessesField(classC.uniqueFieldWithOriginalName("$r8$clinit")));
  }

  static class TestClass {

    public static void main(String[] args) {
      B.test();
    }
  }

  @NoVerticalClassMerging
  static class A {

    static {
      System.out.println("In A.<clinit>()");
    }

    static void inlineable1() {
      System.out.println("In A.inlineable1()");
    }
  }

  @NoVerticalClassMerging
  static class B extends A {

    static {
      System.out.println("In B.<clinit>()");
    }

    @NeverInline
    static void test() {
      A.inlineable1();
      B.inlineable2();
      C.inlineableWithInitClass();
    }

    static void inlineable2() {
      System.out.println("In B.inlineable2()");
    }
  }

  static class C extends B {

    static {
      System.out.println("In C.<clinit>()");
    }

    static void inlineableWithInitClass() {
      System.out.println("In C.inlineableWithInitClass()");
    }
  }
}
