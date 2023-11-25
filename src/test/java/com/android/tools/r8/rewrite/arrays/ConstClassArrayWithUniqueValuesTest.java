// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.rewrite.arrays;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.Iterator;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ConstClassArrayWithUniqueValuesTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public CompilationMode compilationMode;

  @Parameter(2)
  public Integer maxMaterializingConstants;

  @Parameters(name = "{0}, mode = {1}, maxMaterializingConstants = {2}")
  public static Iterable<?> data() {
    return buildParameters(
        getTestParameters().withDefaultCfRuntime().withDexRuntimesAndAllApiLevels().build(),
        CompilationMode.values(),
        ImmutableList.of(Constants.U8BIT_MAX - 16, 2));
  }

  private static final String EXPECTED_OUTPUT =
      StringUtils.lines("[A00, A01, A02, A03, A04]", "[A00, A01, A02, A03, A04]", "100");

  public boolean canUseFilledNewArrayOfClass(TestParameters parameters) {
    return parameters.isDexRuntime()
        && parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.N);
  }

  private enum State {
    EXPECTING_CONSTCLASS,
    EXPECTING_APUTOBJECT
  }

  private void inspect(MethodSubject method, int puts, boolean insideCatchHandler) {
    boolean expectingFilledNewArray =
        canUseFilledNewArrayOfClass(parameters) && !insideCatchHandler;
    assertEquals(
        expectingFilledNewArray ? 0 : puts,
        method.streamInstructions().filter(InstructionSubject::isArrayPut).count());
    assertEquals(
        expectingFilledNewArray ? 1 : 0,
        method.streamInstructions().filter(InstructionSubject::isFilledNewArray).count());
    assertEquals(
        puts, method.streamInstructions().filter(InstructionSubject::isConstClass).count());
    if (!expectingFilledNewArray) {
      // Test that const-string and aput instructions are interleaved by the lowering of
      // filled-new-array.
      int aputCount = 0;
      State state = State.EXPECTING_CONSTCLASS;
      Iterator<InstructionSubject> iterator = method.iterateInstructions();
      while (iterator.hasNext()) {
        InstructionSubject instruction = iterator.next();
        if (instruction.isConstClass()) {
          assertEquals(State.EXPECTING_CONSTCLASS, state);
          state = State.EXPECTING_APUTOBJECT;
        } else if (instruction.isArrayPut()) {
          assertEquals(State.EXPECTING_APUTOBJECT, state);
          state = State.EXPECTING_CONSTCLASS;
          aputCount++;
        }
      }
      assertEquals(State.EXPECTING_CONSTCLASS, state);
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
      Class<?>[] array = {A00.class, A01.class, A02.class, A03.class, A04.class};
      printArray(array);
    }

    @NeverInline
    public static void m2() {
      try {
        Class<?>[] array = {A00.class, A01.class, A02.class, A03.class, A04.class};
        printArray(array);
      } catch (Exception e) {
        throw new RuntimeException();
      }
    }

    @NeverInline
    public static void m3() {
      Class<?>[] array =
          new Class<?>[] {
            A00.class, A01.class, A02.class, A03.class, A04.class, A05.class, A06.class, A07.class,
            A08.class, A09.class, A10.class, A11.class, A12.class, A13.class, A14.class, A15.class,
            A16.class, A17.class, A18.class, A19.class, A20.class, A21.class, A22.class, A23.class,
            A24.class, A25.class, A26.class, A27.class, A28.class, A29.class, A30.class, A31.class,
            A32.class, A33.class, A34.class, A35.class, A36.class, A37.class, A38.class, A39.class,
            A40.class, A41.class, A42.class, A43.class, A44.class, A45.class, A46.class, A47.class,
            A48.class, A49.class, A50.class, A51.class, A52.class, A53.class, A54.class, A55.class,
            A56.class, A57.class, A58.class, A59.class, A60.class, A61.class, A62.class, A63.class,
            A64.class, A65.class, A66.class, A67.class, A68.class, A69.class, A70.class, A71.class,
            A72.class, A73.class, A74.class, A75.class, A76.class, A77.class, A78.class, A79.class,
            A80.class, A81.class, A82.class, A83.class, A84.class, A85.class, A86.class, A87.class,
            A88.class, A89.class, A90.class, A91.class, A92.class, A93.class, A94.class, A95.class,
            A96.class, A97.class, A98.class, A99.class,
          };
      System.out.println(Arrays.asList(array).size());
    }

    @NeverInline
    public static void printArray(Class<?>[] classArray) {
      System.out.print("[");
      for (Class<?> clazz : classArray) {
        if (clazz != classArray[0]) {
          System.out.print(", ");
        }
        String simpleName = clazz.getName();
        if (simpleName.lastIndexOf("$") > 0) {
          simpleName = simpleName.substring(simpleName.lastIndexOf("$") + 1);
        }
        System.out.print(simpleName);
      }
      System.out.println("]");
    }

    public static void main(String[] args) {
      m1();
      m2();
      m3();
    }
  }

  static class A00 {}

  static class A01 {}

  static class A02 {}

  static class A03 {}

  static class A04 {}

  static class A05 {}

  static class A06 {}

  static class A07 {}

  static class A08 {}

  static class A09 {}

  static class A10 {}

  static class A11 {}

  static class A12 {}

  static class A13 {}

  static class A14 {}

  static class A15 {}

  static class A16 {}

  static class A17 {}

  static class A18 {}

  static class A19 {}

  static class A20 {}

  static class A21 {}

  static class A22 {}

  static class A23 {}

  static class A24 {}

  static class A25 {}

  static class A26 {}

  static class A27 {}

  static class A28 {}

  static class A29 {}

  static class A30 {}

  static class A31 {}

  static class A32 {}

  static class A33 {}

  static class A34 {}

  static class A35 {}

  static class A36 {}

  static class A37 {}

  static class A38 {}

  static class A39 {}

  static class A40 {}

  static class A41 {}

  static class A42 {}

  static class A43 {}

  static class A44 {}

  static class A45 {}

  static class A46 {}

  static class A47 {}

  static class A48 {}

  static class A49 {}

  static class A50 {}

  static class A51 {}

  static class A52 {}

  static class A53 {}

  static class A54 {}

  static class A55 {}

  static class A56 {}

  static class A57 {}

  static class A58 {}

  static class A59 {}

  static class A60 {}

  static class A61 {}

  static class A62 {}

  static class A63 {}

  static class A64 {}

  static class A65 {}

  static class A66 {}

  static class A67 {}

  static class A68 {}

  static class A69 {}

  static class A70 {}

  static class A71 {}

  static class A72 {}

  static class A73 {}

  static class A74 {}

  static class A75 {}

  static class A76 {}

  static class A77 {}

  static class A78 {}

  static class A79 {}

  static class A80 {}

  static class A81 {}

  static class A82 {}

  static class A83 {}

  static class A84 {}

  static class A85 {}

  static class A86 {}

  static class A87 {}

  static class A88 {}

  static class A89 {}

  static class A90 {}

  static class A91 {}

  static class A92 {}

  static class A93 {}

  static class A94 {}

  static class A95 {}

  static class A96 {}

  static class A97 {}

  static class A98 {}

  static class A99 {}
}
