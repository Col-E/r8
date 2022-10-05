// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.string;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.KeepConstantArguments;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverPropagateValue;
import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ir.optimize.string.StringValueOfTest.TestClass.Foo;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject.JumboStringMode;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class StringValueOfTest extends TestBase {
  private static final String JAVA_OUTPUT = StringUtils.lines(
      Foo.class.getName(),
      Foo.class.getName(),
      Foo.class.getName(),
      Foo.class.getName(),
      "null",
      "null",
      "null",
      "null",
      "null",
      "null"
  );
  private static final Class<?> MAIN = TestClass.class;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private final TestParameters parameters;

  public StringValueOfTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private void configure(InternalOptions options) {
    options.testing.forceNameReflectionOptimization = true;
  }

  @Test
  public void testJVMOutput() throws Exception {
    assumeTrue("Only run JVM reference on CF runtimes", parameters.isCfRuntime());
    testForJvm()
        .addTestClasspath()
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(JAVA_OUTPUT);
  }

  private long countConstNullNumber(MethodSubject method) {
    return method.streamInstructions().filter(InstructionSubject::isConstNull).count();
  }

  private long countNullStringNumber(MethodSubject method) {
    return method.streamInstructions().filter(instructionSubject ->
        instructionSubject.isConstString("null", JumboStringMode.ALLOW)).count();
  }

  private void test(SingleTestRunResult<?> result, boolean isR8, boolean isRelease)
      throws Exception {
    CodeInspector codeInspector = result.inspector();
    ClassSubject mainClass = codeInspector.clazz(MAIN);
    MethodSubject mainMethod = mainClass.mainMethod();
    assertThat(mainMethod, isPresent());
    int expectedCount = isR8 ? 3 : (isRelease ? 5 : 7);
    assertEquals(expectedCount, countCall(mainMethod, "String", "valueOf"));
    // Due to the different behavior regarding constant canonicalization.
    expectedCount = isR8 ? (parameters.isCfRuntime() ? 2 : 1) : 1;
    assertEquals(expectedCount, countConstNullNumber(mainMethod));
    expectedCount = isR8 ? (parameters.isCfRuntime() ? 2 : 1) : (isRelease ? 1 : 0);
    assertEquals(expectedCount, countNullStringNumber(mainMethod));

    MethodSubject hideNPE = mainClass.uniqueMethodWithOriginalName("hideNPE");
    // Due to the nullable argument, valueOf should remain.
    assertEquals(1, countCall(hideNPE, "String", "valueOf"));

    MethodSubject uninit = mainClass.uniqueMethodWithOriginalName("consumeUninitialized");
    assertThat(uninit, isPresent());
    expectedCount = isR8 ? 0 : 1;
    assertEquals(expectedCount, countCall(uninit, "String", "valueOf"));
    expectedCount = isR8 ? 1 : 0;
    assertEquals(expectedCount, countNullStringNumber(uninit));
  }

  @Test
  public void testR8() throws Exception {
    SingleTestRunResult<?> result =
        testForR8(parameters.getBackend())
            .addProgramClassesAndInnerClasses(MAIN)
            .enableConstantArgumentAnnotations()
            .enableInliningAnnotations()
            .enableMemberValuePropagationAnnotations()
            .addKeepMainRule(MAIN)
            .setMinApi(parameters.getApiLevel())
            .addDontObfuscate()
            .addOptionsModification(this::configure)
            .compile()
            .inspect(
                inspector -> {
                  ClassSubject fooClassSubject = inspector.clazz(Foo.class);
                  assertThat(fooClassSubject, isPresent());
                  assertThat(fooClassSubject.uniqueMethodWithOriginalName("getter"), isAbsent());
                })
            .run(parameters.getRuntime(), MAIN)
            .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, true, true);
  }

  static class TestClass {

    static class Notification {
      String id;
      Notification(String id) {
        this.id = id;
      }

      String getId() {
        return id;
      }
    }

    interface Itf {
      String getter();
    }

    static class Uninitialized {
    }

    @NeverInline
    @NeverPropagateValue
    static String consumeUninitialized(Uninitialized arg) {
      return String.valueOf(arg);
    }

    @KeepConstantArguments
    @NeverInline
    static String hideNPE(String s) {
      return String.valueOf(s);
    }

    static class Foo implements Itf {
      @Override
      public String getter() {
        return String.valueOf(getClass().getName());
      }

      @NeverInline
      @Override
      public String toString() {
        return getter();
      }
    }

    @NeverInline
    static String eventuallyReturnsNull(String s) {
      return System.currentTimeMillis() > 0 ? null : s;
    }

    public static void main(String[] args) {
      Foo foo = new Foo();
      System.out.println(foo.getter());
      // Trivial, it's String.
      String str = foo.toString();
      System.out.println(String.valueOf(str));
      if (str != null) {
        // With an explicit check, it's non-null String.
        System.out.println(String.valueOf(str));
      }
      // The instance itself is not of String type. Outputs are same, though.
      System.out.println(String.valueOf(foo));

      // Simply const-string "null"
      System.out.println(String.valueOf((Object) null));
      try {
        System.out.println(hideNPE(null));
      } catch (NullPointerException npe) {
        throw new AssertionError("Not expected: " + npe);
      }
      try {
        System.out.println(consumeUninitialized(null));
      } catch (NullPointerException npe) {
        throw new AssertionError("Not expected: " + npe);
      }

      // No matter what we pass, that function will return null.
      // But, we're not sure about it, hence not optimizing String#valueOf.
      System.out.println(String.valueOf(eventuallyReturnsNull(null)));
      System.out.println(String.valueOf(eventuallyReturnsNull("non-null")));

      // Eligible for class inlining. Make sure we're optimizing valueOf after class inlining.
      Notification n = new Notification(null);
      System.out.println(String.valueOf(n.getId()));
    }
  }
}
