// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.uninstantiatedtypes;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.google.common.collect.Streams;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Tests that instance method calls are rewritten to {@code throw null} if the type of the receiver
 * is never instantiated directly or indirectly.
 */
@RunWith(Parameterized.class)
public class InvokeMethodWithReceiverOptimizationTest extends TestBase {

  private final Backend backend;

  @Parameters(name = "Backend: {0}")
  public static Backend[] data() {
    return Backend.values();
  }

  public InvokeMethodWithReceiverOptimizationTest(Backend backend) {
    this.backend = backend;
  }

  @Test
  public void test() throws Exception {
    String expected =
        StringUtils.joinLines(
            "Caught NullPointerException from testRewriteToThrowNull",
            "Caught NullPointerException from testRewriteToThrowNullWithCatchHandlers",
            "Caught NullPointerException from testRewriteToThrowNullWithDeadCatchHandler");

    testForJvm().addTestClasspath().run(TestClass.class).assertSuccessWithOutput(expected);

    CodeInspector inspector =
        testForR8(backend)
            .addInnerClasses(InvokeMethodWithReceiverOptimizationTest.class)
            .addKeepMainRule(TestClass.class)
            .enableInliningAnnotations()
            .addOptionsModification(
                options -> {
                  // Avoid that the class inliner inlines testRewriteInvokeVirtualToThrowNullWith-
                  // CatchHandlers(new A()).
                  options.enableClassInlining = false;
                })
            .run(TestClass.class)
            .assertSuccessWithOutput(expected)
            .inspector();

    ClassSubject testClassSubject = inspector.clazz(TestClass.class);
    assertThat(testClassSubject, isPresent());

    ClassSubject otherClassSubject = inspector.clazz(A.class);
    assertThat(otherClassSubject, isPresent());

    // Check that A.method() has been removed.
    assertThat(otherClassSubject.uniqueMethodWithName("method"), not(isPresent()));

    // Check that a throw instruction has been inserted into each of the testRewriteToThrowNull*
    // methods.
    int found = 0;
    for (FoundMethodSubject methodSubject : testClassSubject.allMethods()) {
      if (methodSubject.getOriginalName().startsWith("testRewriteToThrowNull")) {
        assertTrue(
            Streams.stream(methodSubject.iterateInstructions())
                .anyMatch(InstructionSubject::isThrow));
        found++;
      }
    }
    assertEquals(3, found);

    // Check that the method dead() has been removed.
    assertThat(testClassSubject.uniqueMethodWithName("dead"), not(isPresent()));

    // Check that the catch handlers for NullPointerException and RuntimeException have not been
    // removed.
    assertThat(testClassSubject.uniqueMethodWithName("handleNullPointerException"), isPresent());
    assertThat(testClassSubject.uniqueMethodWithName("handleRuntimeException"), isPresent());
  }

  static class TestClass {

    public static void main(String[] args) {
      try {
        testRewriteToThrowNull(null);
      } catch (NullPointerException e) {
        System.out.println("Caught NullPointerException from testRewriteToThrowNull");
      }

      testRewriteToThrowNullWithCatchHandlers(null);

      try {
        testRewriteToThrowNullWithDeadCatchHandler(null);
      } catch (NullPointerException e) {
        System.out.print(
            "Caught NullPointerException from testRewriteToThrowNullWithDeadCatchHandler");
      }
    }

    @NeverInline
    private static void testRewriteToThrowNull(A obj) {
      // Should be rewritten to "throw null".
      String result = obj.method();
      dead(result);
    }

    @NeverInline
    private static void testRewriteToThrowNullWithCatchHandlers(A obj) {
      try {
        // Should be rewritten to "throw null".
        String result = obj.method();
        dead(result);
      } catch (NullPointerException e) {
        // This catch handler cannot be removed.
        handleNullPointerException();
      } catch (RuntimeException e) {
        // This catch handler cannot be removed.
        handleRuntimeException();
      }
    }

    @NeverInline
    private static void handleNullPointerException() {
      System.out.println(
          "Caught NullPointerException from testRewriteToThrowNullWithCatchHandlers");
    }

    @NeverInline
    private static void handleRuntimeException() {
      System.out.println("Caught RuntimeException from testRewriteToThrowNullWithCatchHandlers");
    }

    @NeverInline
    private static void testRewriteToThrowNullWithDeadCatchHandler(A obj) {
      try {
        // Should be rewritten to "throw null".
        String result = obj.method();
        dead(result);
      } catch (CustomException e) {
        // This catch handler should be removed.
        dead("Caught CustomException in testRewriteToThrowNullWithDeadCatchHandler");
      }
    }

    @NeverInline
    private static void dead(String msg) {
      System.out.println("In TestClass.dead(): " + msg);
    }
  }

  static class A {

    @NeverInline
    public String method() {
      return "A.method()";
    }
  }

  static class CustomException extends RuntimeException {}
}
