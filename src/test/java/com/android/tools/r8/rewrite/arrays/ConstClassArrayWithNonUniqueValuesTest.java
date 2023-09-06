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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ConstClassArrayWithNonUniqueValuesTest extends TestBase {

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

  private static final String EXPECTED_OUTPUT = StringUtils.lines("100", "104");

  public boolean canUseFilledNewArrayOfClass(TestParameters parameters) {
    return parameters.isDexRuntime()
        && parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.N);
  }

  private void inspect(
      MethodSubject method, int constClasses, int puts, boolean insideCatchHandler) {
    boolean expectingFilledNewArray =
        canUseFilledNewArrayOfClass(parameters) && !insideCatchHandler;
    assertEquals(
        expectingFilledNewArray ? 0 : puts,
        method.streamInstructions().filter(InstructionSubject::isArrayPut).count());
    assertEquals(
        expectingFilledNewArray ? 1 : 0,
        method.streamInstructions().filter(InstructionSubject::isFilledNewArray).count());
    assertEquals(
        expectingFilledNewArray || parameters.isCfRuntime() ? puts : constClasses,
        method.streamInstructions().filter(InstructionSubject::isConstClass).count());
  }

  private void inspect(CodeInspector inspector) {
    inspect(inspector.clazz(TestClass.class).uniqueMethodWithOriginalName("m1"), 1, 100, false);
    inspect(inspector.clazz(TestClass.class).uniqueMethodWithOriginalName("m2"), 26, 104, false);
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
      Class<?>[] array =
          new Class<?>[] {
            A.class, A.class, A.class, A.class, A.class, A.class, A.class, A.class, A.class,
                A.class,
            A.class, A.class, A.class, A.class, A.class, A.class, A.class, A.class, A.class,
                A.class,
            A.class, A.class, A.class, A.class, A.class, A.class, A.class, A.class, A.class,
                A.class,
            A.class, A.class, A.class, A.class, A.class, A.class, A.class, A.class, A.class,
                A.class,
            A.class, A.class, A.class, A.class, A.class, A.class, A.class, A.class, A.class,
                A.class,
            A.class, A.class, A.class, A.class, A.class, A.class, A.class, A.class, A.class,
                A.class,
            A.class, A.class, A.class, A.class, A.class, A.class, A.class, A.class, A.class,
                A.class,
            A.class, A.class, A.class, A.class, A.class, A.class, A.class, A.class, A.class,
                A.class,
            A.class, A.class, A.class, A.class, A.class, A.class, A.class, A.class, A.class,
                A.class,
            A.class, A.class, A.class, A.class, A.class, A.class, A.class, A.class, A.class,
                A.class,
          };
      System.out.println(Arrays.asList(array).size());
    }

    @NeverInline
    public static void m2() {
      Class<?>[] array =
          new Class<?>[] {
            A.class, B.class, C.class, D.class, E.class, F.class, G.class, H.class, I.class,
            J.class, K.class, L.class, M.class, N.class, O.class, P.class, Q.class, R.class,
            S.class, T.class, U.class, V.class, W.class, X.class, Y.class, Z.class, A.class,
            B.class, C.class, D.class, E.class, F.class, G.class, H.class, I.class, J.class,
            K.class, L.class, M.class, N.class, O.class, P.class, Q.class, R.class, S.class,
            T.class, U.class, V.class, W.class, X.class, Y.class, Z.class, A.class, B.class,
            C.class, D.class, E.class, F.class, G.class, H.class, I.class, J.class, K.class,
            L.class, M.class, N.class, O.class, P.class, Q.class, R.class, S.class, T.class,
            U.class, V.class, W.class, X.class, Y.class, Z.class, A.class, B.class, C.class,
            D.class, E.class, F.class, G.class, H.class, I.class, J.class, K.class, L.class,
            M.class, N.class, O.class, P.class, Q.class, R.class, S.class, T.class, U.class,
            V.class, W.class, X.class, Y.class, Z.class,
          };
      System.out.println(Arrays.asList(array).size());
    }

    public static void main(String[] args) {
      m1();
      m2();
    }
  }

  static class A {}

  static class B {}

  static class C {}

  static class D {}

  static class E {}

  static class F {}

  static class G {}

  static class H {}

  static class I {}

  static class J {}

  static class K {}

  static class L {}

  static class M {}

  static class N {}

  static class O {}

  static class P {}

  static class Q {}

  static class R {}

  static class S {}

  static class T {}

  static class U {}

  static class V {}

  static class W {}

  static class X {}

  static class Y {}

  static class Z {}
}
