// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.ifs;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

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
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EnumAliasComparisonTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public EnumAliasComparisonTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(EnumAliasComparisonTest.class)
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("true", "false", "false", "true", "true", "false");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(TestClass.class);
    assertThat(classSubject, isPresent());

    MethodSubject mainMethod = classSubject.mainMethod();
    assertThat(mainMethod, isPresent());
    assertTrue(
        mainMethod
            .streamInstructions()
            .filter(InstructionSubject::isStaticGet)
            .map(InstructionSubject::getField)
            .map(field -> field.name.toSourceString())
            .allMatch("out"::equals));

    assertThat(inspector.clazz(MyEnum.class), not(isPresent()));
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(MyEnum.A == MyEnum.A);
      System.out.println(MyEnum.A != MyEnum.A);
      System.out.println(MyEnum.A == MyEnum.B);
      System.out.println(MyEnum.A != MyEnum.B);
      System.out.println(MyEnum.A == MyEnum.C);
      System.out.println(MyEnum.A != MyEnum.C);
    }
  }

  enum MyEnum {
    A,
    B;

    // Introduce an alias of MyEnum.A.
    static MyEnum C = A;
  }
}
