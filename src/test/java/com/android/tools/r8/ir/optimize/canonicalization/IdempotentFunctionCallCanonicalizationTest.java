// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.canonicalization;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.D8TestRunResult;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.Streams;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

class IdempotentFunctionMain {
  static int SEED;

  @NeverInline
  static int random(int arg) {
    return SEED * arg;
  }

  @NeverInline
  static int max(int x, int y) {
    return x > y ? x : y;
  }

  public static void main(String[] args) {
    {
      SEED = 0;
      System.out.print(random(2));
      SEED = 1;
      // Should not be canonicalized.
      System.out.print(random(2));

      System.out.print(max(1, 2));

      System.out.print(max(3, 2));
      // Different order of arguments. Not canonicalized.
      System.out.print(max(2, 3));
      // Canonicalized.
      System.out.print(max(3, 2));

      // Canonicalized.
      System.out.print(max(1, 2));

      System.out.println();
    }

    // Primitive will be boxed automatically.
    {
      Map<String, Boolean> map = new HashMap<>();
      // After canonicalization, only one 'true' and one 'false' conversions remain.
      boolean alwaysTrue = System.currentTimeMillis() >= 0;
      boolean alwaysFalse = System.currentTimeMillis() < 0;
      map.put("A", alwaysTrue);
      map.put("B", alwaysTrue);
      map.put("C", alwaysFalse);
      map.put("D", alwaysTrue);
      map.put("E", alwaysFalse);
      map.put("F", alwaysTrue);
      System.out.println(map.get("B"));
      System.out.println(map.get("E"));
    }

    {
      Map<String, Integer> map = new HashMap<>();
      // After canonicalization, only one 8 and one 35 conversions remain.
      map.put("A", 35);
      map.put("B", 8);
      map.put("C", 8);
      map.put("D", 8);
      map.put("E", 35);
      System.out.println(map.get("B"));
      System.out.println(map.get("E"));
    }

    {
      List<Long> ll = new ArrayList<>();
      // After canonicalization, 1, 2, 3, 4, 5, 6, and 9 conversions remain.
      // 3.141592654
      ll.add(3L);
      ll.add(1L);
      ll.add(4L);
      ll.add(1L);
      ll.add(5L);
      ll.add(9L);
      ll.add(2L);
      ll.add(6L);
      ll.add(5L);
      ll.add(4L);
      for (Long l : ll) {
        System.out.print(l);
      }
      System.out.println();
    }
  }
}

@RunWith(Parameterized.class)
public class IdempotentFunctionCallCanonicalizationTest extends TestBase {
  private static final Class<?> MAIN = IdempotentFunctionMain.class;
  private static final String JAVA_OUTPUT = StringUtils.lines(
      "0223332",
      "true",
      "false",
      "8",
      "35",
      "3141592654"
  );
  private static final String BOOLEAN_DESCRIPTOR = "Ljava/lang/Boolean;";
  private static final String INTEGER_DESCRIPTOR = "Ljava/lang/Integer;";
  private static final String LONG_DESCRIPTOR = "Ljava/lang/Long;";
  private static final int EXPECTED_MAX_CALLS = 3;
  private static final int TOTAL_MAX_CALLS = 5;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Parameter(0)
  public TestParameters parameters;

  @Test
  public void testJVMOutput() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addTestClasspath()
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(JAVA_OUTPUT);
  }

  private static boolean isValueOf(DexMethod method, String descriptor) {
    return method.holder.toDescriptorString().equals(descriptor)
        && method.getArity() == 1
        && method.proto.returnType.toDescriptorString().equals(descriptor)
        && method.name.toString().equals("valueOf");
  }

  private static long countValueOf(MethodSubject method, String descriptor) {
    return Streams.stream(method.iterateInstructions(instructionSubject -> {
      if (instructionSubject.isInvoke()) {
        return isValueOf(instructionSubject.getMethod(), descriptor);
      }
      return false;
    })).count();
  }

  private static long countMaxCall(MethodSubject method) {
    return Streams.stream(method.iterateInstructions(instructionSubject -> {
      if (instructionSubject.isInvoke()) {
        return instructionSubject.getMethod().name.toString().equals("max");
      }
      return false;
    })).count();
  }

  private void test(
      SingleTestRunResult<?> result,
      int expectedMaxCount,
      int expectedBooleanValueOfCount,
      int expectedIntValueOfCount,
      int expectedLongValueOfCount)
      throws Exception {
    CodeInspector codeInspector = result.inspector();
    ClassSubject mainClass = codeInspector.clazz(MAIN);
    MethodSubject mainMethod = mainClass.mainMethod();
    assertThat(mainMethod, isPresent());
    assertEquals(expectedMaxCount, countMaxCall(mainMethod));
    assertEquals(expectedBooleanValueOfCount, countValueOf(mainMethod, BOOLEAN_DESCRIPTOR));
    assertEquals(expectedIntValueOfCount, countValueOf(mainMethod, INTEGER_DESCRIPTOR));
    assertEquals(expectedLongValueOfCount, countValueOf(mainMethod, LONG_DESCRIPTOR));
  }

  @Test
  public void testD8() throws Exception {
    assumeTrue("Only run D8 for Dex backend", parameters.isDexRuntime());

    D8TestRunResult result =
        testForD8()
            .addProgramClasses(MAIN)
            .release()
            .setMinApi(parameters)
            .run(parameters.getRuntime(), MAIN)
            .assertSuccessWithOutput(JAVA_OUTPUT);
    test(
        result,
        TOTAL_MAX_CALLS,
        // TODO(b/145259212): Should be `EXPECTED_BOOLEAN_VALUE_OF` (2).
        6,
        // TODO(b/145253152): Should be `EXPECTED_INTEGER_VALUE_OF` (2).
        5,
        // TODO(b/145253152): Should be `EXPECTED_LONG_VALUE_OF` (7).
        10);

    result =
        testForD8()
            .release()
            .addProgramClasses(MAIN)
            .setMinApi(parameters)
            .run(parameters.getRuntime(), MAIN)
            .assertSuccessWithOutput(JAVA_OUTPUT);
    test(
        result,
        TOTAL_MAX_CALLS,
        // TODO(b/145259212): Should be `EXPECTED_BOOLEAN_VALUE_O` (2).
        6,
        // TODO(b/145253152): Should be `EXPECTED_INTEGER_VALUE_OF` (2).
        5,
        // TODO(b/145253152): Should be `EXPECTED_LONG_VALUE_OF` (7).
        10);
  }

  @Test
  public void testR8() throws Exception {
    R8TestRunResult result =
        testForR8(parameters.getBackend())
            .addProgramClasses(MAIN)
            .enableInliningAnnotations()
            .addKeepMainRule(MAIN)
            .addDontObfuscate()
            .setMinApi(parameters)
            .run(parameters.getRuntime(), MAIN)
            .assertSuccessWithOutput(JAVA_OUTPUT);
    int expectedMaxCount = parameters.isCfRuntime() ? TOTAL_MAX_CALLS : EXPECTED_MAX_CALLS;
    // TODO(b/145259212): Should be `EXPECTED_BOOLEAN_VALUE_OF` (2) when compiling for dex.
    int expectedBooleanValueOfCount = 6;
    // TODO(b/145253152): Should be `EXPECTED_INTEGER_VALUE_OF` (2) when compiling for dex.
    int expectedIntValueOfCount = 5;
    // TODO(b/145253152): Should be `EXPECTED_LONG_VALUE_OF` (7) when compiling for dex.
    int expectedLongValueOfCount = 10;
    test(
        result,
        expectedMaxCount,
        expectedBooleanValueOfCount,
        expectedIntValueOfCount,
        expectedLongValueOfCount);
  }
}
