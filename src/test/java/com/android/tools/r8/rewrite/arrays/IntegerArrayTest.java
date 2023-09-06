// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.rewrite.arrays;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class IntegerArrayTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public CompilationMode compilationMode;

  @Parameters(name = "{0}, mode = {1}")
  public static Iterable<?> data() {
    return buildParameters(
        getTestParameters().withDefaultCfRuntime().withDexRuntimesAndAllApiLevels().build(),
        CompilationMode.values());
  }

  private static final String EXPECTED_OUTPUT =
      StringUtils.lines(
          "[-2147483648, -1, 0, 1, 2147483647]", "[-2147483647, -2, 0, 2, 2147483646]");

  public boolean canUseFilledNewArrayOfInteger(TestParameters parameters) {
    return parameters.isDexRuntime();
  }

  private void inspect(MethodSubject main) {
    assertEquals(
        canUseFilledNewArrayOfInteger(parameters) ? 0 : 5,
        main.streamInstructions().filter(InstructionSubject::isArrayPut).count());
    assertEquals(
        canUseFilledNewArrayOfInteger(parameters) ? 1 : 0,
        main.streamInstructions().filter(InstructionSubject::isFilledNewArray).count());
  }

  private void inspect(CodeInspector inspector) {
    inspect(inspector.clazz(TestClass.class).uniqueMethodWithOriginalName("m1"));
    inspect(inspector.clazz(TestClass.class).uniqueMethodWithOriginalName("m2"));
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters.getBackend())
        .addInnerClasses(getClass())
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .inspect(this::inspect)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .enableInliningAnnotations()
        .addDontObfuscate()
        .run(parameters.getRuntime(), TestClass.class)
        .inspect(this::inspect)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  public static final class TestClass {

    @NeverInline
    public static void m1() {
      int[] array = {Integer.MIN_VALUE, -1, 0, 1, Integer.MAX_VALUE};
      printArray(array);
    }

    @NeverInline
    public static void m2() {
      try {
        int[] array = {Integer.MIN_VALUE + 1, -2, 0, 2, Integer.MAX_VALUE - 1};
        printArray(array);
      } catch (Exception e) {
        throw new RuntimeException();
      }
    }

    @NeverInline
    public static void printArray(int[] array) {
      System.out.println(Arrays.toString(array));
    }

    public static void main(String[] args) {
      m1();
      m2();
    }
  }
}
