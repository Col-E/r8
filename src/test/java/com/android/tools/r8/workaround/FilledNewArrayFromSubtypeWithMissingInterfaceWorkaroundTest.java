// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.workaround;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.Dex2OatTestRunResult;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class FilledNewArrayFromSubtypeWithMissingInterfaceWorkaroundTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testD8() throws Exception {
    // D8 test always passes since Main#get is not optimized from having return type A to having
    // return type B.
    parameters.assumeDexRuntime();
    testForD8(parameters.getBackend())
        .addProgramClasses(Main.class, A.class, B.class)
        .release()
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .apply(
            compileResult ->
                compileResult.runDex2Oat(parameters.getRuntime()).assertNoVerificationErrors())
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatThrows(NoClassDefFoundError.class);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class, A.class, B.class)
        .addKeepMainRule(Main.class)
        .addDontWarn(Missing.class)
        .enableInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .applyIf(
            parameters.isDexRuntime(),
            compileResult ->
                compileResult
                    .inspect(this::inspect)
                    .runDex2Oat(parameters.getRuntime())
                    // TODO(b/293501981): Should not fail verification.
                    .applyIf(
                        parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.N),
                        Dex2OatTestRunResult::assertVerificationErrors,
                        Dex2OatTestRunResult::assertNoVerificationErrors))
        .run(parameters.getRuntime(), Main.class)
        .applyIf(
            parameters.isCfRuntime()
                || !parameters.getDexRuntimeVersion().isEqualTo(Version.V13_0_0)
                || parameters.getApiLevel() == AndroidApiLevel.B,
            runResult -> runResult.assertFailureWithErrorThatThrows(NoClassDefFoundError.class),
            runResult -> runResult.assertFailureWithErrorThatThrows(VerifyError.class));
  }

  private void inspect(CodeInspector inspector) {
    MethodSubject mainMethodSubject = inspector.clazz(Main.class).mainMethod();
    assertThat(mainMethodSubject, isPresent());
    assertEquals(
        parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.N),
        mainMethodSubject.streamInstructions().anyMatch(InstructionSubject::isFilledNewArray));
  }

  static class Main {

    public static void main(String[] args) {
      A a = get();
      System.out.println(new A[] {a});
    }

    @NeverInline
    static A get() {
      return new B();
    }
  }

  @NoVerticalClassMerging
  static class A implements Missing {}

  static class B extends A {}

  interface Missing {}
}
