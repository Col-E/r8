// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.regress;

import com.android.tools.r8.NeverPropagateValue;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class Regress160831625Test extends TestBase {

  static final String EXPECTED = StringUtils.lines("A", "0");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  public Regress160831625Test(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testReference() throws Exception {
    testForRuntime(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .setMinApi(parameters)
        .addInnerClasses(getClass())
        .enableMemberValuePropagationAnnotations()
        .addKeepMainRule(TestClass.class)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  enum MyEnum {
    // Ensure that the enum field value is not inlined in the alias in MyClass.B
    @NeverPropagateValue
    A
  }

  static class MyClass {

    private static final MyEnum B = MyEnum.A;

    public static MyEnum getB() {
      return B;
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(MyClass.getB().name());
      System.out.println(MyClass.getB().ordinal());
    }
  }
}
