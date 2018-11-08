// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.uninstantiatedtypes;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Similar to {@link InvokeMethodWithReceiverOptimizationTest}, except that all calls to A.method()
 * have a non-null receiver. Instead, this tests checks that the calls to A.method() are rewritten
 * to throw null when the argument passed to A.method() is null, since A.method() throws a Null-
 * PointerException before any other side-effects when its argument is null.
 *
 * <p>See {@link com.android.tools.r8.graph.DexEncodedMethod.OptimizationInfo#nonNullParamHints}.
 */
@RunWith(Parameterized.class)
public class InvokeMethodWithNonNullParamCheckTest extends TestBase {

  private final Backend backend;

  @Parameters(name = "Backend: {0}")
  public static Collection<Backend> data() {
    return Arrays.asList(Backend.values());
  }

  public InvokeMethodWithNonNullParamCheckTest(Backend backend) {
    this.backend = backend;
  }

  @Test
  public void test() throws Exception {
    String expected =
        StringUtils.joinLines(
            "Caught NullPointerException from testRewriteInvokeStaticToThrowNull",
            "Caught NullPointerException from testRewriteInvokeVirtualToThrowNull",
            "In TestClass.live(): Static.throwIfFirstIsNull()",
            "In TestClass.live(): Virtual.throwIfFirstIsNull()",
            "Caught NullPointerException from testRewriteInvokeStaticToThrowNull"
                + "WithMultipleArguments",
            "Caught NullPointerException from testRewriteInvokeVirtualToThrowNull"
                + "WithMultipleArguments",
            "In TestClass.live(): Static.throwIfSecondIsNull()",
            "In TestClass.live(): Virtual.throwIfFirstIsNull()",
            "Caught NullPointerException from testRewriteInvokeStaticToThrowNull"
                + "WithCatchHandlers",
            "Caught NullPointerException from testRewriteInvokeVirtualToThrowNull"
                + "WithCatchHandlers",
            "Caught NullPointerException from testRewriteInvokeStaticToThrowNull"
                + "WithDeadCatchHandler",
            "Caught NullPointerException from testRewriteInvokeVirtualToThrowNull"
                + "WithDeadCatchHandler");

    testForJvm().addTestClasspath().run(TestClass.class).assertSuccessWithOutput(expected);

    CodeInspector inspector =
        testForR8(backend)
            .addInnerClasses(InvokeMethodWithNonNullParamCheckTest.class)
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

    ClassSubject staticClassSubject = inspector.clazz(Static.class);
    assertThat(staticClassSubject, isPresent());

    ClassSubject virtualClassSubject = inspector.clazz(Virtual.class);
    assertThat(virtualClassSubject, isPresent());

    // Check that a throw instruction has been inserted into each of the testRewriteInvoke* methods.
    int found = 0;
    for (FoundMethodSubject methodSubject : testClassSubject.allMethods()) {
      if (methodSubject.getOriginalName().startsWith("testRewriteInvoke")) {
        boolean shouldHaveThrow = !methodSubject.getOriginalName().contains("NonNullArgument");
        assertEquals(
            shouldHaveThrow,
            Streams.stream(methodSubject.iterateInstructions())
                .anyMatch(InstructionSubject::isThrow));

        if (shouldHaveThrow) {
          // Check that there are no invoke instructions targeting the methods on `Static` and
          // `Virtual`.
          Streams.stream(methodSubject.iterateInstructions())
              .filter(InstructionSubject::isInvoke)
              .forEach(
                  ins -> {
                    ClassSubject clazz = inspector.clazz(ins.getMethod().holder.toSourceString());
                    assertNotEquals(clazz.getOriginalName(), Static.class.getTypeName());
                    assertNotEquals(clazz.getOriginalName(), Virtual.class.getTypeName());
                  });
        }

        found++;
      }
    }
    assertEquals(12, found);

    // Check that the method live() has been kept and that dead() has been removed.
    assertThat(testClassSubject.uniqueMethodWithName("live"), isPresent());
    assertThat(testClassSubject.uniqueMethodWithName("dead"), not(isPresent()));

    // Check that the catch handlers for NullPointerException and RuntimeException have not been
    // removed.
    List<String> methodNames =
        ImmutableList.of(
            "handleNullPointerExceptionForInvokeStatic",
            "handleNullPointerExceptionForInvokeVirtual",
            "handleRuntimeExceptionForInvokeStatic",
            "handleRuntimeExceptionForInvokeVirtual");
    for (String methodName : methodNames) {
      assertThat(testClassSubject.uniqueMethodWithName(methodName), isPresent());
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      try {
        testRewriteInvokeStaticToThrowNull();
      } catch (NullPointerException e) {
        System.out.println("Caught NullPointerException from testRewriteInvokeStaticToThrowNull");
      }

      try {
        testRewriteInvokeVirtualToThrowNull(new Virtual());
      } catch (NullPointerException e) {
        System.out.println("Caught NullPointerException from testRewriteInvokeVirtualToThrowNull");
      }

      testRewriteInvokeStaticToThrowNullWithNonNullArgument();
      testRewriteInvokeVirtualToThrowNullWithNonNullArgument(new Virtual());

      try {
        testRewriteInvokeStaticToThrowNullWithMultipleArguments();
      } catch (NullPointerException e) {
        System.out.println(
            "Caught NullPointerException from testRewriteInvokeStaticToThrowNullWithMultiple"
                + "Arguments");
      }

      try {
        testRewriteInvokeVirtualToThrowNullWithMultipleArguments(new Virtual());
      } catch (NullPointerException e) {
        System.out.println(
            "Caught NullPointerException from testRewriteInvokeVirtualToThrowNullWithMultiple"
                + "Arguments");
      }

      testRewriteInvokeStaticToThrowNullWithMultipleNonNullArguments();
      testRewriteInvokeVirtualToThrowNullWithMultipleNonNullArguments(new Virtual());

      testRewriteInvokeStaticToThrowNullWithCatchHandlers();
      testRewriteInvokeVirtualToThrowNullWithCatchHandlers(new Virtual());

      try {
        testRewriteInvokeStaticToThrowNullWithDeadCatchHandler();
      } catch (NullPointerException e) {
        System.out.println(
            "Caught NullPointerException from "
                + "testRewriteInvokeStaticToThrowNullWithDeadCatchHandler");
      }

      try {
        testRewriteInvokeVirtualToThrowNullWithDeadCatchHandler(new Virtual());
      } catch (NullPointerException e) {
        System.out.print(
            "Caught NullPointerException from "
                + "testRewriteInvokeVirtualToThrowNullWithDeadCatchHandler");
      }
    }

    @NeverInline
    private static void testRewriteInvokeStaticToThrowNull() {
      // Should be rewritten to "throw null".
      String result = Static.throwIfFirstIsNull(null);
      dead(result);
    }

    @NeverInline
    private static void testRewriteInvokeVirtualToThrowNull(Virtual obj) {
      // Should be rewritten to "throw null".
      String result = obj.throwIfFirstIsNull(null);
      dead(result);
    }

    @NeverInline
    private static void testRewriteInvokeStaticToThrowNullWithNonNullArgument() {
      // Should *not* be rewritten to "throw null".
      String result = Static.throwIfFirstIsNull(new Object());
      live(result);
    }

    @NeverInline
    private static void testRewriteInvokeVirtualToThrowNullWithNonNullArgument(Virtual obj) {
      // Should *not* be rewritten to "throw null".
      String result = obj.throwIfFirstIsNull(new Object());
      live(result);
    }

    @NeverInline
    private static void testRewriteInvokeStaticToThrowNullWithMultipleArguments() {
      // Should be rewritten to "throw null".
      String result = Static.throwIfSecondIsNull(new Object(), null, new Object());
      dead(result);
    }

    @NeverInline
    private static void testRewriteInvokeVirtualToThrowNullWithMultipleArguments(Virtual obj) {
      // Should be rewritten to "throw null".
      String result = obj.throwIfSecondIsNull(new Object(), null, new Object());
      dead(result);
    }

    @NeverInline
    private static void testRewriteInvokeStaticToThrowNullWithMultipleNonNullArguments() {
      // Should *not* be rewritten to "throw null".
      String result = Static.throwIfSecondIsNull(new Object(), new Object(), new Object());
      live(result);
    }

    @NeverInline
    private static void testRewriteInvokeVirtualToThrowNullWithMultipleNonNullArguments(
        Virtual obj) {
      // Should *not* be rewritten to "throw null".
      String result = obj.throwIfSecondIsNull(new Object(), new Object(), new Object());
      live(result);
    }

    @NeverInline
    private static void testRewriteInvokeStaticToThrowNullWithCatchHandlers() {
      try {
        // Should be rewritten to "throw null".
        String result = Static.throwIfFirstIsNull(null);
        dead(result);
      } catch (NullPointerException e) {
        // This catch handler cannot be removed.
        handleNullPointerExceptionForInvokeStatic();
      } catch (RuntimeException e) {
        // This catch handler cannot be removed.
        handleRuntimeExceptionForInvokeStatic();
      }
    }

    @NeverInline
    private static void handleNullPointerExceptionForInvokeStatic() {
      System.out.println(
          "Caught NullPointerException from testRewriteInvokeStaticToThrowNullWithCatchHandlers");
    }

    @NeverInline
    private static void handleRuntimeExceptionForInvokeStatic() {
      System.out.println(
          "Caught RuntimeException from testRewriteInvokeStaticToThrowNullWithCatchHandlers");
    }

    @NeverInline
    private static void testRewriteInvokeVirtualToThrowNullWithCatchHandlers(Virtual obj) {
      try {
        // Should be rewritten to "throw null".
        String result = obj.throwIfFirstIsNull(null);
        dead(result);
      } catch (NullPointerException e) {
        // This catch handler cannot be removed.
        handleNullPointerExceptionForInvokeVirtual();
      } catch (RuntimeException e) {
        // This catch handler cannot be removed.
        handleRuntimeExceptionForInvokeVirtual();
      }
    }

    @NeverInline
    private static void handleNullPointerExceptionForInvokeVirtual() {
      System.out.println(
          "Caught NullPointerException from testRewriteInvokeVirtualToThrowNullWithCatchHandlers");
    }

    @NeverInline
    private static void handleRuntimeExceptionForInvokeVirtual() {
      System.out.println(
          "Caught RuntimeException from testRewriteInvokeVirtualToThrowNullWithCatchHandlers");
    }

    @NeverInline
    private static void testRewriteInvokeStaticToThrowNullWithDeadCatchHandler() {
      try {
        // Should be rewritten to "throw null".
        String result = Static.throwIfFirstIsNull(null);
        dead(result);
      } catch (CustomException e) {
        // This catch handler should be removed.
        dead("Caught CustomException in testRewriteInvokeStaticToThrowNullWithDeadCatchHandler");
      }
    }

    @NeverInline
    private static void testRewriteInvokeVirtualToThrowNullWithDeadCatchHandler(Virtual obj) {
      try {
        // Should be rewritten to "throw null".
        String result = obj.throwIfFirstIsNull(null);
        dead(result);
      } catch (CustomException e) {
        // This catch handler should be removed.
        dead("Caught CustomException in testRewriteInvokeVirtualToThrowNullWithDeadCatchHandler");
      }
    }

    @NeverInline
    private static void live(String msg) {
      System.out.println("In TestClass.live(): " + msg);
    }

    @NeverInline
    private static void dead(String msg) {
      System.out.println("In TestClass.dead(): " + msg);
    }
  }

  static class Static {

    @NeverInline
    public static String throwIfFirstIsNull(Object first) {
      if (first == null) {
        throw new NullPointerException();
      }
      return "Static.throwIfFirstIsNull()";
    }

    @NeverInline
    public static String throwIfSecondIsNull(Object first, Object second, Object third) {
      if (second == null) {
        throw new NullPointerException();
      }
      // Use `first` and `third` for something to prevent them from being removed.
      if (System.currentTimeMillis() < 0) {
        System.out.println(first);
        System.out.println(third);
      }
      return "Static.throwIfSecondIsNull()";
    }
  }

  static class Virtual {

    @NeverInline
    public String throwIfFirstIsNull(Object first) {
      if (first == null) {
        throw new NullPointerException();
      }
      return "Virtual.throwIfFirstIsNull()";
    }

    @NeverInline
    public String throwIfSecondIsNull(Object first, Object second, Object third) {
      if (second == null) {
        throw new NullPointerException();
      }
      // Use `first` and `third` for something to prevent them from being removed.
      if (System.currentTimeMillis() < 0) {
        System.out.println(first);
        System.out.println(third);
      }
      return "Virtual.throwIfFirstIsNull()";
    }
  }

  static class CustomException extends RuntimeException {}
}
