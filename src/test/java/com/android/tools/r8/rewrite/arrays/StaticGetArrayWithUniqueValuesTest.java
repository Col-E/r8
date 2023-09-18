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
public class StaticGetArrayWithUniqueValuesTest extends TestBase {

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

  public boolean canUseFilledNewArrayOfObject(TestParameters parameters) {
    return parameters.isDexRuntime()
        && parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.N);
  }

  private enum State {
    EXPECTING_GETSTATIC,
    EXPECTING_APUTOBJECT
  }

  private void inspect(MethodSubject method, int puts, boolean insideCatchHandler) {
    boolean expectingFilledNewArray =
        canUseFilledNewArrayOfObject(parameters) && !insideCatchHandler;
    assertEquals(
        expectingFilledNewArray ? 0 : puts,
        method.streamInstructions().filter(InstructionSubject::isArrayPut).count());
    assertEquals(
        expectingFilledNewArray ? 1 : 0,
        method.streamInstructions().filter(InstructionSubject::isFilledNewArray).count());
    assertEquals(puts, method.streamInstructions().filter(InstructionSubject::isStaticGet).count());
    if (!expectingFilledNewArray) {
      // Test that sget and aput instructions are interleaved by the lowering of
      // filled-new-array.
      int aputCount = 0;
      State state = State.EXPECTING_GETSTATIC;
      Iterator<InstructionSubject> iterator = method.iterateInstructions();
      while (iterator.hasNext()) {
        InstructionSubject instruction = iterator.next();
        if (instruction.isStaticGet()) {
          assertEquals(State.EXPECTING_GETSTATIC, state);
          state = State.EXPECTING_APUTOBJECT;
        } else if (instruction.isArrayPut()) {
          assertEquals(State.EXPECTING_APUTOBJECT, state);
          state = State.EXPECTING_GETSTATIC;
          aputCount++;
        }
      }
      assertEquals(State.EXPECTING_GETSTATIC, state);
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
      A[] array = {A.A00, A.A01, A.A02, A.A03, A.A04};
      printArray(array);
    }

    @NeverInline
    public static void m2() {
      try {
        A[] array = {A.A00, A.A01, A.A02, A.A03, A.A04};
        printArray(array);
      } catch (Exception e) {
        throw new RuntimeException();
      }
    }

    @NeverInline
    public static void m3() {
      A[] array =
          new A[] {
            A.A00, A.A01, A.A02, A.A03, A.A04, A.A05, A.A06, A.A07,
            A.A08, A.A09, A.A10, A.A11, A.A12, A.A13, A.A14, A.A15,
            A.A16, A.A17, A.A18, A.A19, A.A20, A.A21, A.A22, A.A23,
            A.A24, A.A25, A.A26, A.A27, A.A28, A.A29, A.A30, A.A31,
            A.A32, A.A33, A.A34, A.A35, A.A36, A.A37, A.A38, A.A39,
            A.A40, A.A41, A.A42, A.A43, A.A44, A.A45, A.A46, A.A47,
            A.A48, A.A49, A.A50, A.A51, A.A52, A.A53, A.A54, A.A55,
            A.A56, A.A57, A.A58, A.A59, A.A60, A.A61, A.A62, A.A63,
            A.A64, A.A65, A.A66, A.A67, A.A68, A.A69, A.A70, A.A71,
            A.A72, A.A73, A.A74, A.A75, A.A76, A.A77, A.A78, A.A79,
            A.A80, A.A81, A.A82, A.A83, A.A84, A.A85, A.A86, A.A87,
            A.A88, A.A89, A.A90, A.A91, A.A92, A.A93, A.A94, A.A95,
            A.A96, A.A97, A.A98, A.A99,
          };
      printArraySize(array);
    }

    @NeverInline
    public static void printArray(A[] array) {
      System.out.print("[");
      for (A a : array) {
        if (a != array[0]) {
          System.out.print(", ");
        }
        System.out.print(a);
      }
      System.out.println("]");
    }

    @NeverInline
    public static void printArraySize(A[] array) {
      System.out.println(Arrays.asList(array).size());
    }

    public static void main(String[] args) {
      m1();
      m2();
      m3();
    }
  }

  static class A {
    private final String name;

    private A(String name) {
      this.name = name;
    }

    public String toString() {
      return name;
    }

    static A A00 = new A("A00");
    static A A01 = new A("A01");
    static A A02 = new A("A02");
    static A A03 = new A("A03");
    static A A04 = new A("A04");
    static A A05 = new A("A05");
    static A A06 = new A("A06");
    static A A07 = new A("A07");
    static A A08 = new A("A08");
    static A A09 = new A("A09");
    static A A10 = new A("A10");
    static A A11 = new A("A11");
    static A A12 = new A("A12");
    static A A13 = new A("A13");
    static A A14 = new A("A14");
    static A A15 = new A("A15");
    static A A16 = new A("A16");
    static A A17 = new A("A17");
    static A A18 = new A("A18");
    static A A19 = new A("A19");
    static A A20 = new A("A20");
    static A A21 = new A("A21");
    static A A22 = new A("A22");
    static A A23 = new A("A23");
    static A A24 = new A("A24");
    static A A25 = new A("A25");
    static A A26 = new A("A26");
    static A A27 = new A("A27");
    static A A28 = new A("A28");
    static A A29 = new A("A29");
    static A A30 = new A("A30");
    static A A31 = new A("A31");
    static A A32 = new A("A32");
    static A A33 = new A("A33");
    static A A34 = new A("A34");
    static A A35 = new A("A35");
    static A A36 = new A("A36");
    static A A37 = new A("A37");
    static A A38 = new A("A38");
    static A A39 = new A("A39");
    static A A40 = new A("A40");
    static A A41 = new A("A41");
    static A A42 = new A("A42");
    static A A43 = new A("A43");
    static A A44 = new A("A44");
    static A A45 = new A("A45");
    static A A46 = new A("A46");
    static A A47 = new A("A47");
    static A A48 = new A("A48");
    static A A49 = new A("A49");
    static A A50 = new A("A50");
    static A A51 = new A("A51");
    static A A52 = new A("A52");
    static A A53 = new A("A53");
    static A A54 = new A("A54");
    static A A55 = new A("A55");
    static A A56 = new A("A56");
    static A A57 = new A("A57");
    static A A58 = new A("A58");
    static A A59 = new A("A59");
    static A A60 = new A("A60");
    static A A61 = new A("A61");
    static A A62 = new A("A62");
    static A A63 = new A("A63");
    static A A64 = new A("A64");
    static A A65 = new A("A65");
    static A A66 = new A("A66");
    static A A67 = new A("A67");
    static A A68 = new A("A68");
    static A A69 = new A("A69");
    static A A70 = new A("A70");
    static A A71 = new A("A71");
    static A A72 = new A("A72");
    static A A73 = new A("A73");
    static A A74 = new A("A74");
    static A A75 = new A("A75");
    static A A76 = new A("A76");
    static A A77 = new A("A77");
    static A A78 = new A("A78");
    static A A79 = new A("A79");
    static A A80 = new A("A80");
    static A A81 = new A("A81");
    static A A82 = new A("A82");
    static A A83 = new A("A83");
    static A A84 = new A("A84");
    static A A85 = new A("A85");
    static A A86 = new A("A86");
    static A A87 = new A("A87");
    static A A88 = new A("A88");
    static A A89 = new A("A89");
    static A A90 = new A("A90");
    static A A91 = new A("A91");
    static A A92 = new A("A92");
    static A A93 = new A("A93");
    static A A94 = new A("A94");
    static A A95 = new A("A95");
    static A A96 = new A("A96");
    static A A97 = new A("A97");
    static A A98 = new A("A98");
    static A A99 = new A("A99");
  }

  // public static void main(String[] args) {
  //   for (int i = 0; i < 100; i++) {
  //     System.out.println("    static A A" + i + " = new A(\"A" + i + "\")");
  //   }
  // }
}
