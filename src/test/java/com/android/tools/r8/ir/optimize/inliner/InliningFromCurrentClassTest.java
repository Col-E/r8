// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethod;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverMerge;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;

public class InliningFromCurrentClassTest extends TestBase {

  @Test
  public void test() throws Exception {
    String expectedOutput =
        StringUtils.lines(
            "In A.<clinit>()",
            "In B.<clinit>()",
            "In A.inlineable1()",
            "In B.inlineable2()",
            "In C.<clinit>()",
            "In C.notInlineable()");

    testForJvm().addTestClasspath().run(TestClass.class).assertSuccessWithOutput(expectedOutput);

    CodeInspector inspector =
        testForR8(Backend.DEX)
            .addInnerClasses(InliningFromCurrentClassTest.class)
            .addKeepMainRule(TestClass.class)
            .enableInliningAnnotations()
            .enableMergeAnnotations()
            .run(TestClass.class)
            .assertSuccessWithOutput(expectedOutput)
            .inspector();

    ClassSubject classA = inspector.clazz(A.class);
    assertThat(classA, isPresent());

    ClassSubject classB = inspector.clazz(B.class);
    assertThat(classB, isPresent());

    ClassSubject classC = inspector.clazz(C.class);
    assertThat(classC, isPresent());

    MethodSubject inlineable1Method = classA.uniqueMethodWithName("inlineable1");
    assertThat(inlineable1Method, not(isPresent()));

    MethodSubject inlineable2Method = classB.uniqueMethodWithName("inlineable2");
    assertThat(inlineable2Method, not(isPresent()));

    MethodSubject notInlineableMethod = classC.uniqueMethodWithName("notInlineable");
    assertThat(notInlineableMethod, isPresent());

    MethodSubject testMethod = classB.uniqueMethodWithName("test");
    assertThat(testMethod, isPresent());
    assertThat(testMethod, invokesMethod(notInlineableMethod));
  }

  static class TestClass {

    public static void main(String[] args) {
      B.test();
    }
  }

  @NeverMerge
  static class A {

    static {
      System.out.println("In A.<clinit>()");
    }

    static void inlineable1() {
      System.out.println("In A.inlineable1()");
    }
  }

  @NeverMerge
  static class B extends A {

    static {
      System.out.println("In B.<clinit>()");
    }

    @NeverInline
    static void test() {
      A.inlineable1();
      B.inlineable2();
      C.notInlineable();
    }

    static void inlineable2() {
      System.out.println("In B.inlineable2()");
    }
  }

  static class C extends B {

    static {
      System.out.println("In C.<clinit>()");
    }

    static void notInlineable() {
      System.out.println("In C.notInlineable()");
    }
  }
}
