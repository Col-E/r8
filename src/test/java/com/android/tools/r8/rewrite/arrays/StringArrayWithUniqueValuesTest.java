// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.rewrite.arrays;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.Arrays;
import java.util.Iterator;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class StringArrayWithUniqueValuesTest extends TestBase {

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
      StringUtils.lines("[A, B, C, D, E]", "[F, G, H, I, J]", "100");

  public boolean canUseFilledNewArrayOfStrings(TestParameters parameters) {
    return parameters.isDexRuntime()
        && parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.K);
  }

  private enum State {
    EXPECTING_CONSTSTRING,
    EXPECTING_APUTOBJECT
  }

  private void inspect(MethodSubject method, int puts, boolean insideCatchHandler) {
    boolean expectingFilledNewArray =
        canUseFilledNewArrayOfStrings(parameters) && !insideCatchHandler;
    assertEquals(
        expectingFilledNewArray ? 0 : puts,
        method.streamInstructions().filter(InstructionSubject::isArrayPut).count());
    assertEquals(
        expectingFilledNewArray ? 1 : 0,
        method.streamInstructions().filter(InstructionSubject::isFilledNewArray).count());
    assertEquals(
        puts, method.streamInstructions().filter(InstructionSubject::isConstString).count());
    if (!expectingFilledNewArray) {
      // Test that const-string and aput instructions are interleaved by the lowering of
      // filled-new-array.
      int aputCount = 0;
      State state = State.EXPECTING_CONSTSTRING;
      Iterator<InstructionSubject> iterator = method.iterateInstructions();
      while (iterator.hasNext()) {
        InstructionSubject instruction = iterator.next();
        if (instruction.isConstString()) {
          assertEquals(State.EXPECTING_CONSTSTRING, state);
          state = State.EXPECTING_APUTOBJECT;
        } else if (instruction.isArrayPut()) {
          assertEquals(State.EXPECTING_APUTOBJECT, state);
          state = State.EXPECTING_CONSTSTRING;
          aputCount++;
        }
      }
      assertEquals(State.EXPECTING_CONSTSTRING, state);
      assertEquals(puts, aputCount);
    }
  }

  private void inspect(CodeInspector inspector) {
    inspect(inspector.clazz(TestClass.class).uniqueMethodWithOriginalName("m1"), 5, false);
    inspect(inspector.clazz(TestClass.class).uniqueMethodWithOriginalName("m2"), 5, true);
    inspect(inspector.clazz(TestClass.class).uniqueMethodWithOriginalName("m3"), 100, false);
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
      String[] stringArray = {"A", "B", "C", "D", "E"};
      System.out.println(Arrays.asList(stringArray));
    }

    @NeverInline
    public static void m2() {
      try {
        String[] stringArray = {"F", "G", "H", "I", "J"};
        System.out.println(Arrays.asList(stringArray));
      } catch (Exception e) {
        throw new RuntimeException();
      }
    }

    @NeverInline
    public static void m3() {
      String[] array =
          new String[] {
            "0", "1", "2", "3", "4", "5", "6", "7", "8", "9",
            "10", "11", "12", "13", "14", "15", "16", "17", "18", "19",
            "20", "21", "22", "23", "24", "25", "26", "27", "28", "29",
            "30", "31", "32", "33", "34", "35", "36", "37", "38", "39",
            "40", "41", "42", "43", "44", "45", "46", "47", "48", "49",
            "50", "51", "52", "53", "54", "55", "56", "57", "58", "59",
            "60", "61", "62", "63", "64", "65", "66", "67", "68", "69",
            "70", "71", "72", "73", "74", "75", "76", "77", "78", "79",
            "80", "81", "82", "83", "84", "85", "86", "87", "88", "89",
            "90", "91", "92", "93", "94", "95", "96", "97", "98", "99",
          };
      System.out.println(Arrays.asList(array).size());
    }

    public static void main(String[] args) {
      m1();
      m2();
      m3();
    }
  }
}
