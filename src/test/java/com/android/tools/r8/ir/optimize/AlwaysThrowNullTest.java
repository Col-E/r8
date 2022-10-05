// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.D8TestRunResult;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverPropagateValue;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

class AlwaysThrowNullTestClass {
  @NoVerticalClassMerging
  static class Base {
    Object dead;

    void foo() {
      System.out.println("Base::foo");
    }
  }

  static class Uninitialized extends Base {
  }

  @NeverPropagateValue
  @NeverInline
  static void uninstantiatedInstancePutWithNPEGuard(Uninitialized arg) {
    try {
      arg.dead = new Object();
    } catch (NullPointerException npe) {
      System.out.println("Expected NPE");
    }
  }

  @NeverPropagateValue
  @NeverInline
  static void uninstantiatedInstancePutWithOtherGuard(Uninitialized arg) {
    try {
      arg.dead = new Object();
    } catch (IllegalArgumentException e) {
      throw new AssertionError("Unexpected exception kind");
    }
  }

  @NeverPropagateValue
  @NeverInline
  static void uninstantiatedInstanceInvoke(Uninitialized arg) {
    arg.foo();
    // Dead code.
    System.out.println("invoke-virtual with null receiver: " + arg.dead.toString());
  }

  static Object nullObject() {
    return null;
  }

  @NeverPropagateValue
  @NeverInline
  static void nullReferenceInvoke() {
    try {
      Object o = nullObject();
      o.hashCode();
      // Dead code.
      System.out.println("invoke-virtual with null receiver: " + o.toString());
    } catch (NullPointerException npe) {
      System.out.println("Expected NPE");
    }
  }

  @NeverPropagateValue
  @NeverInline
  static void uninstantiatedInstancePut(Uninitialized arg) {
    arg.dead = new Object();
    // Dead code.
    System.out.println("instance-put with null receiver: " + arg.dead.toString());
  }

  @NeverPropagateValue
  @NeverInline
  static void uninstantiatedInstanceGet(Uninitialized arg) {
    Object dead = arg.dead;
    // Dead code.
    System.out.println("instance-get with null receiver: " + dead.toString());
  }

  public static void main(String[] args) {
    Base b = new Base();
    try {
      uninstantiatedInstancePutWithNPEGuard(null);
    } catch (NullPointerException npe) {
      throw new AssertionError("Unexpected NPE");
    }
    try {
      uninstantiatedInstancePutWithOtherGuard(null);
      throw new AssertionError("Expected NullPointerException");
    } catch (NullPointerException npe) {
      System.out.println("Expected NPE");
    }
    try {
      uninstantiatedInstanceInvoke(null);
      throw new AssertionError("Expected NullPointerException");
    } catch (NullPointerException npe) {
      System.out.println("Expected NPE");
    }
    try {
      nullReferenceInvoke();
    } catch (NullPointerException npe) {
      throw new AssertionError("Unexpected NPE");
    }
    try {
      uninstantiatedInstancePut(null);
      throw new AssertionError("Expected NullPointerException");
    } catch (NullPointerException npe) {
      System.out.println("Expected NPE");
    }
    try {
      uninstantiatedInstanceGet(null);
      throw new AssertionError("Expected NullPointerException");
    } catch (NullPointerException npe) {
      System.out.println("Expected NPE");
    }
  }
}

@RunWith(Parameterized.class)
public class AlwaysThrowNullTest extends TestBase {
  private static final String JAVA_OUTPUT = StringUtils.lines(
      "Expected NPE",
      "Expected NPE",
      "Expected NPE",
      "Expected NPE",
      "Expected NPE",
      "Expected NPE"
  );
  private static final Class<?> MAIN = AlwaysThrowNullTestClass.class;
  private static final List<String> METHODS_WITH_NPE_GUARD = ImmutableList.of(
      "uninstantiatedInstancePutWithNPEGuard",
      "nullReferenceInvoke"
  );
  private static final List<String> METHODS_WITHOUT_GUARD = ImmutableList.of(
      "uninstantiatedInstanceInvoke",
      "uninstantiatedInstancePut",
      "uninstantiatedInstanceGet"
  );

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private final TestParameters parameters;

  public AlwaysThrowNullTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testJvmOutput() throws Exception {
    assumeTrue("Only run JVM reference on CF runtimes", parameters.isCfRuntime());
    testForJvm()
        .addTestClasspath()
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(JAVA_OUTPUT);
  }

  private void test(SingleTestRunResult<?> result, boolean hasLiveness) throws Exception {
    CodeInspector codeInspector = result.inspector();
    ClassSubject mainClass = codeInspector.clazz(MAIN);

    int expectedThrow = hasLiveness ? 1 : 0;
    for (String methodName : METHODS_WITH_NPE_GUARD) {
      MethodSubject withNPEGuard = mainClass.uniqueMethodWithOriginalName(methodName);
      assertThat(withNPEGuard, isPresent());
      // catch handlers could be split, and thus not always 1, but some small positive numbers.
      assertTrue(
        Streams.stream(
            withNPEGuard.iterateTryCatches(
                t -> t.isCatching(NullPointerException.class.getName()))
        ).count() > 0);
      assertEquals(
          expectedThrow,
          Streams.stream(withNPEGuard.iterateInstructions(InstructionSubject::isThrow)).count());
    }

    int expectedHandler = hasLiveness ? 0 : 1;
    MethodSubject withOtherGuard =
        mainClass.uniqueMethodWithOriginalName("uninstantiatedInstancePutWithOtherGuard");
    assertThat(withOtherGuard, isPresent());
    assertEquals(expectedHandler, Streams.stream(withOtherGuard.iterateTryCatches()).count());

    for (String methodName : METHODS_WITHOUT_GUARD) {
      MethodSubject mtd = mainClass.uniqueMethodWithOriginalName(methodName);
      assertThat(mtd, isPresent());
      assertEquals(
          hasLiveness,
          Streams.stream(mtd.iterateInstructions(InstructionSubject::isInvoke)).count() == 0);
      assertEquals(
          expectedThrow,
          Streams.stream(mtd.iterateInstructions(InstructionSubject::isThrow)).count());
    }
  }

  @Test
  public void testD8() throws Exception {
    assumeTrue("Only run D8 for Dex backend", parameters.isDexRuntime());
    D8TestRunResult result =
        testForD8()
            .release()
            .addProgramClassesAndInnerClasses(MAIN)
            .setMinApi(parameters.getApiLevel())
            .run(parameters.getRuntime(), MAIN)
            .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, false);
  }

  @Test
  public void testR8() throws Exception {
    R8TestRunResult result =
        testForR8(parameters.getBackend())
            .addProgramClassesAndInnerClasses(MAIN)
            .enableNoVerticalClassMergingAnnotations()
            .enableInliningAnnotations()
            .enableMemberValuePropagationAnnotations()
            .addKeepMainRule(MAIN)
            .addDontObfuscate()
            .setMinApi(parameters.getApiLevel())
            .run(parameters.getRuntime(), MAIN)
            .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, true);
  }
}
