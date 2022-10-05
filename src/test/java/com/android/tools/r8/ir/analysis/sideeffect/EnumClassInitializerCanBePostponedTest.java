// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.sideeffect;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EnumClassInitializerCanBePostponedTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public EnumClassInitializerCanBePostponedTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(EnumClassInitializerCanBePostponedTest.class)
        .addKeepMainRule(TestClass.class)
        .addOptionsModification(options -> options.testing.enableSwitchToIfRewriting = false)
        .enableInliningAnnotations()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(TestClass.class);
    assertThat(classSubject, isPresent());
    assertThat(classSubject.uniqueMethodWithOriginalName("dead"), not(isPresent()));

    for (String methodName : ImmutableList.of("testIf", "testSwitch")) {
      MethodSubject methodSubject = classSubject.uniqueMethodWithOriginalName(methodName);
      assertThat(methodSubject, isPresent());

      // Verify that all enum reads have been removed.
      assertTrue(
          methodSubject
              .streamInstructions()
              .filter(InstructionSubject::isStaticGet)
              .map(InstructionSubject::getField)
              .map(DexField::toSourceString)
              .allMatch("java.io.PrintStream java.lang.System.out"::equals));

      // Verify that there are no more conditional jumps.
      assertTrue(methodSubject.streamInstructions().noneMatch(InstructionSubject::isIf));
      assertTrue(methodSubject.streamInstructions().noneMatch(InstructionSubject::isSwitch));
    }

    assertThat(inspector.clazz(MyEnum.class), not(isPresent()));
  }

  static class TestClass {

    public static void main(String[] args) {
      testIf();
      testSwitch();
    }

    @NeverInline
    static void testIf() {
      MyEnum value = MyEnum.A;
      if (value == MyEnum.A) {
        System.out.print("Hello");
      } else {
        dead();
      }
      if (value == MyEnum.B) {
        dead();
      }
    }

    @NeverInline
    static void testSwitch() {
      switch (MyEnum.A) {
        case A:
          System.out.println(" world!");
          break;

        case B:
          dead();
          break;

        default:
          dead();
          break;
      }
    }

    @NeverInline
    static void dead() {
      throw new RuntimeException();
    }
  }

  enum MyEnum {
    A,
    B
  }
}
