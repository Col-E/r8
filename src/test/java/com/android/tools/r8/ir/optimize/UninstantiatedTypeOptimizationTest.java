// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;

public class UninstantiatedTypeOptimizationTest extends TestBase {

  @Test
  public void test() throws Exception {
    String expected =
        StringUtils.joinLines(
            "Caught NullPointerException from foo",
            "Caught NullPointerException from bar",
            "Caught NullPointerException from baz");

    testForJvm().addTestClasspath().run(TestClass.class).assertSuccessWithOutput(expected);

    CodeInspector inspector =
        testForR8(Backend.DEX)
            .addInnerClasses(UninstantiatedTypeOptimizationTest.class)
            .addKeepMainRule(TestClass.class)
            .enableInliningAnnotations()
            .addOptionsModification(opt -> opt.enableMinification = false)
            .run(TestClass.class)
            .assertSuccessWithOutput(expected)
            .inspector();

    ClassSubject classSubject = inspector.clazz(TestClass.class);
    assertThat(classSubject, isPresent());

    // Check that the method dead() has been removed.
    assertThat(classSubject.method("void", "dead"), not(isPresent()));

    // Check that the catch handlers for NullPointerException and RuntimeException have not been
    // removed.
    assertThat(classSubject.method("void", "handleNullPointerExceptionFromBar"), isPresent());
    assertThat(classSubject.method("void", "handleRuntimeExceptionFromBar"), isPresent());

    // Check that the catch handler for CustomException has been removed.
    assertThat(classSubject.method("void", "handleCustomException"), not(isPresent()));
  }

  static class TestClass {

    public static void main(String[] args) {
      try {
        foo(null);
      } catch (NullPointerException e) {
        System.out.println("Caught NullPointerException from foo");
      }

      bar(null);

      try {
        baz(null);
      } catch (NullPointerException e) {
        System.out.print("Caught NullPointerException from baz");
      }
    }

    @NeverInline
    private static void foo(A obj) {
      // Should be rewritten to "throw null".
      String result = obj.foo();
      dead(result);
    }

    @NeverInline
    private static void bar(A obj) {
      try {
        // Should be rewritten to "throw null".
        String result = obj.bar();
        dead(result);
      } catch (NullPointerException e) {
        // This catch handler cannot be removed.
        handleNullPointerExceptionFromBar();
      } catch (RuntimeException e) {
        // This catch handler cannot be removed.
        handleRuntimeExceptionFromBar();
      }
    }

    @NeverInline
    private static void baz(A obj) {
      try {
        // Should be rewritten to "throw null".
        String result = obj.baz();
        dead(result);
      } catch (CustomException e) {
        // This catch handler should be removed.
        handleCustomException();
      }
    }

    @NeverInline
    private static void dead(String msg) {
      System.out.println("In TestClass.dead(): " + msg);
    }

    @NeverInline
    private static void handleNullPointerExceptionFromBar() {
      System.out.println("Caught NullPointerException from bar");
    }

    @NeverInline
    private static void handleRuntimeExceptionFromBar() {
      System.out.println("Caught RuntimeException from bar");
    }

    @NeverInline
    private static void handleCustomException() {
      System.out.println("Caught CustomException");
    }
  }

  static class A {

    @NeverInline
    public String foo() {
      return "A.foo()";
    }

    @NeverInline
    public String bar() {
      return "A.bar()";
    }

    @NeverInline
    public String baz() {
      return "A.baz()";
    }
  }

  static class CustomException extends RuntimeException {}
}
