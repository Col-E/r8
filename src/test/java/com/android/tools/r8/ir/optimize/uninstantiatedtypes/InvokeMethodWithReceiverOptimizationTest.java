// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.uninstantiatedtypes;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.google.common.collect.Streams;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * Tests that instance method calls are rewritten to {@code throw null} if the type of the receiver
 * is never instantiated directly or indirectly.
 */
@RunWith(Parameterized.class)
public class InvokeMethodWithReceiverOptimizationTest extends TestBase {

  private static final String EXPECTED_OUTPUT =
      StringUtils.joinLines(
          "Caught NullPointerException from testRewriteToThrowNull",
          "Caught NullPointerException from testRewriteToThrowNullWithCatchHandlers",
          "Caught NullPointerException from testRewriteToThrowNullWithDeadCatchHandler");

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean enableArgumentPropagation;

  @Parameters(name = "{0}, argument propagation: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), BooleanUtils.values());
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
            .addInnerClasses(InvokeMethodWithReceiverOptimizationTest.class)
            .addKeepMainRule(TestClass.class)
            .enableInliningAnnotations()
            .addOptionsModification(
                options ->
                    options.callSiteOptimizationOptions().setEnabled(enableArgumentPropagation))
            // TODO(b/120764902): The calls to getOriginalName() below does not work in presence of
            //  argument removal.
            .addDontObfuscate()
            .setMinApi(parameters)
            .run(parameters.getRuntime(), TestClass.class)
            .assertSuccessWithOutput(EXPECTED_OUTPUT)
            .inspector();

    ClassSubject testClassSubject = inspector.clazz(TestClass.class);
    assertThat(testClassSubject, isPresent());

    ClassSubject otherClassSubject = inspector.clazz(A.class);
    assertNotEquals(enableArgumentPropagation, otherClassSubject.isPresent());

    // Check that A.method() has been removed.
    assertThat(otherClassSubject.uniqueMethodWithOriginalName("method"), not(isPresent()));

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
    assertThat(testClassSubject.uniqueMethodWithOriginalName("dead"), not(isPresent()));

    // Check that the catch handlers for NullPointerException and RuntimeException have not been
    // removed.
    assertThat(
        testClassSubject.uniqueMethodWithOriginalName("handleNullPointerException"), isPresent());
    assertThat(
        testClassSubject.uniqueMethodWithOriginalName("handleRuntimeException"), isPresent());
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
