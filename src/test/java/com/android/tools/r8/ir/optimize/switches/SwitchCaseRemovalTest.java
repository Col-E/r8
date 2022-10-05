// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.switches;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverPropagateValue;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class SwitchCaseRemovalTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public SwitchCaseRemovalTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class)
        .addKeepMainRule(TestClass.class)
        .addKeepRules(
            "-assumevalues class " + TestClass.class.getTypeName() + " {",
            "  public static final int x return 0..42;",
            "}")
        .addOptionsModification(
            options -> {
              options.testing.enableSwitchToIfRewriting = false;
              options.testing.enableDeadSwitchCaseElimination = true;
            })
        .enableInliningAnnotations()
        .enableMemberValuePropagationAnnotations()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(this::verifyOutput)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(StringUtils.times(StringUtils.lines("Hello world!"), 3));
  }

  private void verifyOutput(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(TestClass.class);
    assertThat(classSubject, isPresent());
    assertThat(classSubject.uniqueMethodWithOriginalName("dead"), not(isPresent()));

    {
      MethodSubject methodSubject =
          classSubject.uniqueMethodWithOriginalName("testSwitchCaseRemoval");
      assertThat(methodSubject, isPresent());
      assertEquals(
          1, methodSubject.streamInstructions().filter(InstructionSubject::isConstNull).count());
      assertEquals(
          2, methodSubject.streamInstructions().filter(InstructionSubject::isReturnObject).count());
      verifyUniqueSwitchHasExactCases(methodSubject.buildIR(), ImmutableSet.of(0));
    }

    {
      MethodSubject methodSubject =
          classSubject.uniqueMethodWithOriginalName("testSwitchReplacementWithExplicitDefaultCase");
      assertThat(methodSubject, isPresent());
      assertTrue(methodSubject.streamInstructions().noneMatch(InstructionSubject::isSwitch));
    }

    {
      MethodSubject methodSubject =
          classSubject.uniqueMethodWithOriginalName(
              "testSwitchReplacementWithoutExplicitDefaultCase");
      assertThat(methodSubject, isPresent());
      assertTrue(methodSubject.streamInstructions().noneMatch(InstructionSubject::isSwitch));
    }
  }

  private void verifyUniqueSwitchHasExactCases(IRCode code, Set<Integer> expectedCases) {
    Streams.stream(code.instructions())
        .filter(Instruction::isIntSwitch)
        .map(Instruction::asIntSwitch)
        .forEach(
            theSwitch -> {
              assertEquals(expectedCases.size(), theSwitch.numberOfKeys());
              for (int i : theSwitch.getKeys()) {
                assertTrue(expectedCases.contains(i));
              }
            });
  }

  static class TestClass {

    public static final int x = System.currentTimeMillis() >= 0 ? 0 : 42;

    public static void main(String[] args) {
      System.out.println(testSwitchCaseRemoval());
      System.out.println(testSwitchReplacementWithExplicitDefaultCase());
      System.out.println(testSwitchReplacementWithoutExplicitDefaultCase());
    }

    @NeverInline
    public static String testSwitchCaseRemoval() {
      switch (x) {
        case 0:
          return "Hello world!";
        case 1: // TODO(b/132420434): Verify that this is removed.
          return null;
        case 43:
          return dead();
        case 2: // TODO(b/132420434): Verify that this is removed.
        default:
          return null;
      }
    }

    @NeverInline
    @NeverPropagateValue
    public static String testSwitchReplacementWithExplicitDefaultCase() {
      switch (x) {
        case 43:
          return dead();
        default:
          return "Hello world!";
      }
    }

    @NeverInline
    @NeverPropagateValue
    public static String testSwitchReplacementWithoutExplicitDefaultCase() {
      switch (x) {
        case 43:
          return dead();
      }
      return "Hello world!";
    }

    @NeverInline
    @NeverPropagateValue
    public static String dead() {
      return "WTF";
    }
  }
}
