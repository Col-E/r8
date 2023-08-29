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
public class StringArrayTest extends TestBase {

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
      StringUtils.lines("[A, B, C, D, E]", "[F, G, H, I, J]");

  public boolean canUseFilledNewArrayOfStrings(TestParameters parameters) {
    return parameters.isDexRuntime()
        && parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.K);
  }

  private enum State {
    EXPECTING_CONSTSTRING,
    EXPECTING_APUTOBJECT
  }

  private void inspect(MethodSubject method, boolean insideCatchHandler) {
    boolean expectingFilledNewArray =
        canUseFilledNewArrayOfStrings(parameters) && !insideCatchHandler;
    assertEquals(
        expectingFilledNewArray ? 0 : 5,
        method.streamInstructions().filter(InstructionSubject::isArrayPut).count());
    assertEquals(
        expectingFilledNewArray ? 1 : 0,
        method.streamInstructions().filter(InstructionSubject::isFilledNewArray).count());
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
      assertEquals(5, aputCount);
    }
  }

  private void inspect(CodeInspector inspector) {
    inspect(inspector.clazz(TestClass.class).uniqueMethodWithOriginalName("m1"), false);
    inspect(inspector.clazz(TestClass.class).uniqueMethodWithOriginalName("m2"), true);
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

    public static void main(String[] args) {
      m1();
      m2();
    }
  }
}
