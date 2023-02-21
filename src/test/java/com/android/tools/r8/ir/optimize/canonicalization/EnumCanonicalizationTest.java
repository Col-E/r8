// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.canonicalization;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EnumCanonicalizationTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public EnumCanonicalizationTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(EnumCanonicalizationTest.class)
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("A", "A", "A", "Class initialization!", "A", "A", "A");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(TestClass.class);
    assertThat(classSubject, isPresent());

    ClassSubject enumSubject = inspector.clazz(MyEnum.class);
    assertThat(enumSubject, isPresent());

    FieldSubject enumFieldSubject = enumSubject.uniqueFieldWithOriginalName("A");
    assertThat(enumFieldSubject, isPresent());

    ClassSubject enumWithClassInitializationSideEffectsSubject =
        inspector.clazz(MyEnumWithClassInitializationSideEffects.class);
    assertThat(enumWithClassInitializationSideEffectsSubject, isPresent());

    FieldSubject enumWithClassInitializationSideEffectsFieldSubject =
        enumWithClassInitializationSideEffectsSubject.uniqueFieldWithOriginalName("A");
    assertThat(enumWithClassInitializationSideEffectsFieldSubject, isPresent());

    MethodSubject mainMethodSubject = classSubject.mainMethod();
    assertThat(mainMethodSubject, isPresent());
    assertEquals(
        1,
        mainMethodSubject
            .streamInstructions()
            .filter(InstructionSubject::isStaticGet)
            .map(InstructionSubject::getField)
            .filter(enumFieldSubject.getField().getReference()::equals)
            .count());
    assertEquals(
        1,
        mainMethodSubject
            .streamInstructions()
            .filter(InstructionSubject::isStaticGet)
            .map(InstructionSubject::getField)
            .filter(
                enumWithClassInitializationSideEffectsFieldSubject.getField().getReference()
                    ::equals)
            .count());
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(MyEnum.A);
      System.out.println(MyEnum.A);
      System.out.println(MyEnum.A);
      System.out.println(MyEnumWithClassInitializationSideEffects.A);
      System.out.println(MyEnumWithClassInitializationSideEffects.A);
      System.out.println(MyEnumWithClassInitializationSideEffects.A);
    }
  }

  enum MyEnum {
    A
  }

  enum MyEnumWithClassInitializationSideEffects {
    A;

    static {
      System.out.println("Class initialization!");
    }
  }
}
