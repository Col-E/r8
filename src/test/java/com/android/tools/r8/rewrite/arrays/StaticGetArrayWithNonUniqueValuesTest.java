// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.rewrite.arrays;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompilerBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class StaticGetArrayWithNonUniqueValuesTest extends TestBase {

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

  private static final String EXPECTED_OUTPUT = StringUtils.lines("100", "50");

  public boolean canUseFilledNewArrayOfObject(TestParameters parameters) {
    return parameters.isDexRuntime()
        && parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.N);
  }

  private void inspect(MethodSubject method, int staticGets, int puts, boolean insideCatchHandler) {
    boolean expectingFilledNewArray =
        canUseFilledNewArrayOfObject(parameters) && !insideCatchHandler;
    assertEquals(
        expectingFilledNewArray ? 0 : puts,
        method.streamInstructions().filter(InstructionSubject::isArrayPut).count());
    assertEquals(
        expectingFilledNewArray ? 1 : 0,
        method.streamInstructions().filter(InstructionSubject::isFilledNewArray).count());
    assertEquals(
        staticGets, method.streamInstructions().filter(InstructionSubject::isStaticGet).count());
  }

  private void configure(TestCompilerBuilder<?, ?, ?, ?, ?> builder) {
    builder.addOptionsModification(
        options ->
            options.rewriteArrayOptions().maxMaterializingConstants = maxMaterializingConstants);
  }

  private void inspectD8(CodeInspector inspector) {
    inspect(
        inspector.clazz(TestClass.class).uniqueMethodWithOriginalName("m1"),
        canUseFilledNewArrayOfObject(parameters) ? 100 : 1,
        100,
        false);
    inspect(
        inspector.clazz(TestClass.class).uniqueMethodWithOriginalName("m2"),
        canUseFilledNewArrayOfObject(parameters) ? 50 : (maxMaterializingConstants == 2 ? 42 : 10),
        50,
        false);
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters.getBackend())
        .addInnerClasses(getClass())
        .setMinApi(parameters)
        .apply(this::configure)
        .run(parameters.getRuntime(), TestClass.class)
        .inspect(this::inspectD8)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  private void inspectR8(CodeInspector inspector) {
    inspect(inspector.clazz(TestClass.class).uniqueMethodWithOriginalName("m1"), 1, 100, false);
    inspect(
        inspector.clazz(TestClass.class).uniqueMethodWithOriginalName("m2"),
        (parameters.isCfRuntime() && maxMaterializingConstants == 2) ? 42 : 10,
        50,
        false);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .enableInliningAnnotations()
        .addDontObfuscate()
        .apply(this::configure)
        .run(parameters.getRuntime(), TestClass.class)
        .inspect(this::inspectR8)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  public static final class TestClass {

    @NeverInline
    public static void m1() {
      A[] array =
          new A[] {
            A.A00, A.A00, A.A00, A.A00, A.A00, A.A00, A.A00, A.A00, A.A00, A.A00,
            A.A00, A.A00, A.A00, A.A00, A.A00, A.A00, A.A00, A.A00, A.A00, A.A00,
            A.A00, A.A00, A.A00, A.A00, A.A00, A.A00, A.A00, A.A00, A.A00, A.A00,
            A.A00, A.A00, A.A00, A.A00, A.A00, A.A00, A.A00, A.A00, A.A00, A.A00,
            A.A00, A.A00, A.A00, A.A00, A.A00, A.A00, A.A00, A.A00, A.A00, A.A00,
            A.A00, A.A00, A.A00, A.A00, A.A00, A.A00, A.A00, A.A00, A.A00, A.A00,
            A.A00, A.A00, A.A00, A.A00, A.A00, A.A00, A.A00, A.A00, A.A00, A.A00,
            A.A00, A.A00, A.A00, A.A00, A.A00, A.A00, A.A00, A.A00, A.A00, A.A00,
            A.A00, A.A00, A.A00, A.A00, A.A00, A.A00, A.A00, A.A00, A.A00, A.A00,
            A.A00, A.A00, A.A00, A.A00, A.A00, A.A00, A.A00, A.A00, A.A00, A.A00,
          };
      printArraySize(array);
    }

    @NeverInline
    public static void m2() {
      A[] array =
          new A[] {
            A.A00, A.A01, A.A02, A.A03, A.A04, A.A05, A.A06, A.A07, A.A08, A.A09,
            A.A00, A.A01, A.A02, A.A03, A.A04, A.A05, A.A06, A.A07, A.A08, A.A09,
            A.A00, A.A01, A.A02, A.A03, A.A04, A.A05, A.A06, A.A07, A.A08, A.A09,
            A.A00, A.A01, A.A02, A.A03, A.A04, A.A05, A.A06, A.A07, A.A08, A.A09,
            A.A00, A.A01, A.A02, A.A03, A.A04, A.A05, A.A06, A.A07, A.A08, A.A09,
          };
      printArraySize(array);
    }

    @NeverInline
    public static void printArraySize(A[] array) {
      System.out.println(Arrays.asList(array).size());
    }

    public static void main(String[] args) {
      m1();
      m2();
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
  }
}
