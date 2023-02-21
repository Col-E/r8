// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.regress.b150688800;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverPropagateValue;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class IdempotentCountErrorTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("0.0");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public IdempotentCountErrorTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .enableInliningAnnotations()
        .enableMemberValuePropagationAnnotations()
        .addInnerClasses(IdempotentCountErrorTest.class)
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  static class TestClass {

    @NeverInline
    @NeverPropagateValue
    public static double twoInputValues(double x, double y) {
      return x + y;
    }

    public static void main(String[] args) {
      if (args.length > 42) {
        System.out.println(twoInputValues(0, 0));
      } else {
        System.out.println(twoInputValues(0, 0));
      }
    }
  }
}
