// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.string;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.ForceInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverPropagateValue;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject.JumboStringMode;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.Streams;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

class StringValueOfTestMain {

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

  @NeverInline
  static String hideNPE(String s) {
    return String.valueOf(s);
  }

  static class Foo implements Itf {
    @ForceInline
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
      fail("Not expected: " + npe);
    }
    try {
      System.out.println(consumeUninitialized(null));
    } catch (NullPointerException npe) {
      fail("Not expected: " + npe);
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

@RunWith(Parameterized.class)
public class StringValueOfTest extends TestBase {
  private static final String JAVA_OUTPUT = StringUtils.lines(
      "com.android.tools.r8.ir.optimize.string.StringValueOfTestMain$Foo",
      "com.android.tools.r8.ir.optimize.string.StringValueOfTestMain$Foo",
      "com.android.tools.r8.ir.optimize.string.StringValueOfTestMain$Foo",
      "com.android.tools.r8.ir.optimize.string.StringValueOfTestMain$Foo",
      "null",
      "null",
      "null",
      "null",
      "null",
      "null"
  );
  private static final Class<?> MAIN = StringValueOfTestMain.class;

  private static final String STRING_DESCRIPTOR = "Ljava/lang/String;";

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().build();
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

  private static boolean isStringValueOf(DexMethod method) {
    return method.holder.toDescriptorString().equals(STRING_DESCRIPTOR)
        && method.getArity() == 1
        && method.proto.returnType.toDescriptorString().equals(STRING_DESCRIPTOR)
        && method.name.toString().equals("valueOf");
  }

  private long countStringValueOf(MethodSubject method) {
    return Streams.stream(method.iterateInstructions(instructionSubject -> {
      if (instructionSubject.isInvoke()) {
        return isStringValueOf(instructionSubject.getMethod());
      }
      return false;
    })).count();
  }

  private long countConstNullNumber(MethodSubject method) {
    return Streams.stream(method.iterateInstructions(InstructionSubject::isConstNull)).count();
  }

  private long countNullStringNumber(MethodSubject method) {
    return Streams.stream(method.iterateInstructions(instructionSubject ->
        instructionSubject.isConstString("null", JumboStringMode.ALLOW))).count();
  }

  private void test(
      TestRunResult result,
      int expectedStringValueOfCountInMain,
      int expectedNullCount,
      int expectedNullStringCountInMain,
      int expectedStringValueOfCountInConsumer,
      int expectedNullStringCountInConsumer)
      throws Exception {
    CodeInspector codeInspector = result.inspector();
    ClassSubject mainClass = codeInspector.clazz(MAIN);
    MethodSubject mainMethod = mainClass.mainMethod();
    assertThat(mainMethod, isPresent());
    assertEquals(expectedStringValueOfCountInMain, countStringValueOf(mainMethod));
    assertEquals(expectedNullCount, countConstNullNumber(mainMethod));
    assertEquals(expectedNullStringCountInMain, countNullStringNumber(mainMethod));

    MethodSubject hideNPE = mainClass.uniqueMethodWithName("hideNPE");
    assertThat(hideNPE, isPresent());
    // Due to the nullable argument, valueOf should remain.
    assertEquals(1, countStringValueOf(hideNPE));

    MethodSubject uninit = mainClass.uniqueMethodWithName("consumeUninitialized");
    assertThat(uninit, isPresent());
    assertEquals(expectedStringValueOfCountInConsumer, countStringValueOf(uninit));
    assertEquals(expectedNullStringCountInConsumer, countNullStringNumber(uninit));
  }

  @Test
  public void testD8() throws Exception {
    assumeTrue("Only run D8 for Dex backend", parameters.isDexRuntime());

    TestRunResult result =
        testForD8()
            .debug()
            .addProgramClassesAndInnerClasses(MAIN)
            .setMinApi(parameters.getRuntime())
            .addOptionsModification(options -> options.enableNonNullTracking = true)
            .run(parameters.getRuntime(), MAIN)
            .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, 7, 1, 0, 1, 0);

    result =
        testForD8()
            .release()
            .addProgramClassesAndInnerClasses(MAIN)
            .setMinApi(parameters.getRuntime())
            .addOptionsModification(options -> options.enableNonNullTracking = true)
            .run(parameters.getRuntime(), MAIN)
            .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, 5, 1, 1, 1, 0);
  }

  @Test
  public void testR8() throws Exception {
    TestRunResult result =
        testForR8(parameters.getBackend())
            .addProgramClassesAndInnerClasses(MAIN)
            .enableInliningAnnotations()
            .enableMemberValuePropagationAnnotations()
            .addKeepMainRule(MAIN)
            .setMinApi(parameters.getRuntime())
            .noMinification()
            .addOptionsModification(this::configure)
            .run(parameters.getRuntime(), MAIN)
            .assertSuccessWithOutput(JAVA_OUTPUT);
    // Due to the different behavior regarding constant canonicalization.
    int expectedNullCount = parameters.isCfRuntime() ? 2 : 1;
    int expectedNullStringCount = parameters.isCfRuntime() ? 2 : 1;
    test(result, 3, expectedNullCount, expectedNullStringCount, 0, 1);
  }
}
