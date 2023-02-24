// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.redundantarraygetelimination;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RedundantArrayGetEliminationTest extends TestBase {

  private static final String EXPECTED_OUTPUT = StringUtils.lines("0", "0", "0", "42", "84");

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8()
        .addInnerClasses(getClass())
        .release()
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepClassAndMembersRules(Main.class)
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject mainClassSubject = inspector.clazz(Main.class);
    assertArrayGetCountEquals(
        1, mainClassSubject.uniqueMethodWithOriginalName("testRedundantArrayGet"));
    assertArrayGetCountEquals(
        1,
        mainClassSubject.uniqueMethodWithOriginalName(
            "testRedundantArrayGetAfterPutToUnrelatedArray"));
    assertArrayGetCountEquals(
        1,
        mainClassSubject.uniqueMethodWithOriginalName(
            "testRedundantArrayGetAfterPutToUnrelatedIndex"));
    assertArrayGetCountEquals(
        2,
        mainClassSubject.uniqueMethodWithOriginalName("testNecessaryArrayGetAfterAliasedArrayPut"));
    assertArrayGetCountEquals(
        2,
        mainClassSubject.uniqueMethodWithOriginalName(
            "testNecessaryArrayGetAfterExternalSideEffect"));
  }

  static void assertArrayGetCountEquals(int expected, MethodSubject methodSubject) {
    assertEquals(
        expected,
        methodSubject.streamInstructions().filter(InstructionSubject::isArrayGet).count());
  }

  static class Main {

    public static void main(String[] args) {
      int[] ints = new int[] {0, 1};
      long[] longs = new long[] {0};
      testRedundantArrayGet(ints, 0);
      testRedundantArrayGetAfterPutToUnrelatedArray(ints, longs, 0);
      testRedundantArrayGetAfterPutToUnrelatedIndex(ints, ints);
      testNecessaryArrayGetAfterAliasedArrayPut(ints, ints, 0, 0);
      testNecessaryArrayGetAfterExternalSideEffect(ints, 0, () -> ints[0] = 42);
    }

    static void testRedundantArrayGet(int[] array, int index) {
      int first = array[index];
      int second = array[index]; // Redundant.
      System.out.println(first + second);
    }

    static void testRedundantArrayGetAfterPutToUnrelatedArray(
        int[] array, long[] otherArray, int index) {
      int first = array[index];
      otherArray[index] = 42;
      int second = array[index]; // Redundant.
      System.out.println(first + second);
    }

    static void testRedundantArrayGetAfterPutToUnrelatedIndex(int[] array, int[] sameArray) {
      int first = array[0];
      sameArray[1] = 42;
      int second = array[0]; // Redundant.
      System.out.println(first + second);
    }

    static void testNecessaryArrayGetAfterAliasedArrayPut(
        int[] array, int[] sameArray, int index, int sameIndex) {
      int first = array[index];
      sameArray[sameIndex] = 42;
      int second = array[index]; // Not redundant.
      System.out.println(first + second);
    }

    static void testNecessaryArrayGetAfterExternalSideEffect(
        int[] array, int index, Runnable runnable) {
      int first = array[index];
      runnable.run();
      int second = array[index]; // Not redundant.
      System.out.println(first + second);
    }
  }
}
