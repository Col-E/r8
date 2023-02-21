// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.reflection;

import static org.junit.Assert.assertFalse;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class GetClassBaseAndSubTest extends TestBase {

  static final String EXPECTED =
      StringUtils.lines("class " + Base.class.getTypeName(), "class " + Sub.class.getTypeName());

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public GetClassBaseAndSubTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testReference() throws Exception {
    testForRuntime(parameters)
        .addInnerClasses(GetClassBaseAndSubTest.class)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addDontObfuscate()
        .addInnerClasses(GetClassBaseAndSubTest.class)
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED)
        .inspect(
            inspector ->
                assertFalse(
                    inspector
                        .clazz(TestClass.class)
                        .mainMethod()
                        .streamInstructions()
                        .anyMatch(InstructionSubject::isConstClass)));
  }

  static class Base {}

  static class Sub extends Base {}

  static class TestClass {

    public static void main(String[] args) {
      Base baseWithBase = System.currentTimeMillis() > 0 ? new Base() : new Sub();
      // Cannot be rewritten to const-class.
      System.out.println(baseWithBase.getClass());
      Base baseWithSub = System.currentTimeMillis() > 0 ? new Sub() : new Base();
      // Cannot be rewritten to const-class.
      System.out.println(baseWithSub.getClass());
    }
  }
}
