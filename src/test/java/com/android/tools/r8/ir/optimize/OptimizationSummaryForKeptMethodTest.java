// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethod;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class OptimizationSummaryForKeptMethodTest extends TestBase {

  private final boolean enableExtraKeepRule;
  private final TestParameters parameters;

  @Parameters(name = "{1}, enable extra keep rule: {0}")
  public static List<Object[]> params() {
    return buildParameters(BooleanUtils.values(), getTestParameters().withAllRuntimes().build());
  }

  public OptimizationSummaryForKeptMethodTest(
      boolean enableExtraKeepRule, TestParameters parameters) {
    this.enableExtraKeepRule = enableExtraKeepRule;
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(OptimizationSummaryForKeptMethodTest.class)
        .addKeepMainRule(TestClass.class)
        .addKeepClassAndMembersRules(
            (Class<?>[]) (enableExtraKeepRule ? new Class<?>[] {KeptClass.class} : new Class<?>[0]))
        .enableInliningAnnotations()
        .setMinApi(parameters.getRuntime())
        .compile()
        .inspect(this::verifyOutput);
  }

  private void verifyOutput(CodeInspector inspector) {
    ClassSubject testClassSubject = inspector.clazz(TestClass.class);
    assertThat(testClassSubject, isPresent());

    MethodSubject mainMethodSubject = testClassSubject.mainMethod();
    assertThat(mainMethodSubject, isPresent());

    // Main method invokes noNormalExits() since it is not allowed to be inlined.
    assertThat(
        mainMethodSubject,
        invokesMethod(
            inspector.clazz(KeptClass.class).uniqueMethodWithOriginalName("noNormalExits")));

    // The fact that noNormalExits() never returns normally has only been exploited if it is not
    // kept.
    assertNotEquals(
        enableExtraKeepRule,
        mainMethodSubject.streamInstructions().anyMatch(InstructionSubject::isThrow));
  }

  static class TestClass {

    public static void main(String[] args) {
      try {
        KeptClass.noNormalExits();
        System.out.println(" world!");
      } catch (Exception e) {
        System.out.println("Caught " + e.getClass().getName());
      }
    }
  }

  static class KeptClass {

    @NeverInline
    public static void noNormalExits() {
      throw new RuntimeException();
    }
  }
}
