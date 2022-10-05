// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.library;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.google.common.base.Predicates.or;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class BooleanValueOfTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public BooleanValueOfTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(BooleanValueOfTest.class)
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("true", "false", "true", "false", "true", "false");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject testClassSubject = inspector.clazz(TestClass.class);
    assertThat(testClassSubject, isPresent());

    MethodSubject testBooleanValueOfTrue =
        testClassSubject.uniqueMethodWithOriginalName("testBooleanValueOfTrue");
    assertThat(testBooleanValueOfTrue, isPresent());
    assertTrue(
        testBooleanValueOfTrue
            .streamInstructions()
            .filter(InstructionSubject::isInvoke)
            .map(invoke -> invoke.getMethod().name.toSourceString())
            .noneMatch("booleanValue"::equals));
    assertTrue(
        testBooleanValueOfTrue
            .streamInstructions()
            .filter(InstructionSubject::isStaticGet)
            .map(staticGet -> staticGet.getField().name.toSourceString())
            .noneMatch("TRUE"::equals));

    MethodSubject testBooleanValueOfFalse =
        testClassSubject.uniqueMethodWithOriginalName("testBooleanValueOfFalse");
    assertThat(testBooleanValueOfFalse, isPresent());
    assertTrue(
        testBooleanValueOfFalse
            .streamInstructions()
            .filter(InstructionSubject::isInvoke)
            .map(invoke -> invoke.getMethod().name.toSourceString())
            .noneMatch("booleanValue"::equals));
    assertTrue(
        testBooleanValueOfFalse
            .streamInstructions()
            .filter(InstructionSubject::isStaticGet)
            .map(staticGet -> staticGet.getField().name.toSourceString())
            .noneMatch("FALSE"::equals));

    MethodSubject testRoundTripTrueMethodSubject =
        testClassSubject.uniqueMethodWithOriginalName("testRoundTripTrue");
    assertThat(testRoundTripTrueMethodSubject, isPresent());
    assertTrue(
        testRoundTripTrueMethodSubject
            .streamInstructions()
            .filter(InstructionSubject::isInvoke)
            .map(invoke -> invoke.getMethod().name.toSourceString())
            .noneMatch(or("booleanValue"::equals, "valueOf"::equals)));

    MethodSubject testRoundTripFalseMethodSubject =
        testClassSubject.uniqueMethodWithOriginalName("testRoundTripFalse");
    assertThat(testRoundTripFalseMethodSubject, isPresent());
    assertTrue(
        testRoundTripFalseMethodSubject
            .streamInstructions()
            .filter(InstructionSubject::isInvoke)
            .map(invoke -> invoke.getMethod().name.toSourceString())
            .noneMatch(or("booleanValue"::equals, "valueOf"::equals)));

    MethodSubject testValueOfTrue =
        testClassSubject.uniqueMethodWithOriginalName("testValueOfTrue");
    assertThat(testValueOfTrue, isPresent());
    assertTrue(
        testValueOfTrue
            .streamInstructions()
            .filter(InstructionSubject::isInvoke)
            .map(invoke -> invoke.getMethod().name.toSourceString())
            .noneMatch("valueOf"::equals));
    assertTrue(
        testValueOfTrue
            .streamInstructions()
            .filter(InstructionSubject::isStaticGet)
            .map(staticGet -> staticGet.getField().name.toSourceString())
            .anyMatch("TRUE"::equals));

    MethodSubject testValueOfFalse =
        testClassSubject.uniqueMethodWithOriginalName("testValueOfFalse");
    assertThat(testValueOfFalse, isPresent());
    assertTrue(
        testValueOfFalse
            .streamInstructions()
            .filter(InstructionSubject::isInvoke)
            .map(invoke -> invoke.getMethod().name.toSourceString())
            .noneMatch("valueOf"::equals));
    assertTrue(
        testValueOfFalse
            .streamInstructions()
            .filter(InstructionSubject::isStaticGet)
            .map(staticGet -> staticGet.getField().name.toSourceString())
            .anyMatch("FALSE"::equals));
  }

  static class TestClass {

    public static void main(String[] args) {
      testBooleanValueOfTrue();
      testBooleanValueOfFalse();
      testRoundTripTrue();
      testRoundTripFalse();
      testValueOfTrue();
      testValueOfFalse();
    }

    @NeverInline
    static void testBooleanValueOfTrue() {
      System.out.println(Boolean.TRUE.booleanValue());
    }

    @NeverInline
    static void testBooleanValueOfFalse() {
      System.out.println(Boolean.FALSE.booleanValue());
    }

    @NeverInline
    static void testRoundTripTrue() {
      System.out.println(Boolean.valueOf(true).booleanValue());
    }

    @NeverInline
    static void testRoundTripFalse() {
      System.out.println(Boolean.valueOf(false).booleanValue());
    }

    @NeverInline
    static void testValueOfTrue() {
      System.out.println(Boolean.valueOf(true));
    }

    @NeverInline
    static void testValueOfFalse() {
      System.out.println(Boolean.valueOf(false));
    }
  }
}
