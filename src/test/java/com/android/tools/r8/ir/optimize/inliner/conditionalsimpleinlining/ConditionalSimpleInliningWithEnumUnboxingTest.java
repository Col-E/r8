// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner.conditionalsimpleinlining;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runners.Parameterized.Parameters;

public class ConditionalSimpleInliningWithEnumUnboxingTest
    extends ConditionalSimpleInliningTestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return TestBase.getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ConditionalSimpleInliningWithEnumUnboxingTest(TestParameters parameters) {
    super(true, parameters);
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .apply(this::configure)
        .addEnumUnboxingInspector(inspector -> inspector.assertUnboxed(EnumUnboxingCandidate.class))
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  static class TestClass {

    public static void main(String[] args) {
      EnumUnboxingCandidate value =
          System.currentTimeMillis() > 0 ? EnumUnboxingCandidate.A : EnumUnboxingCandidate.B;
      simpleIfNullTest(value);
    }

    static void simpleIfNullTest(EnumUnboxingCandidate arg) {
      if (arg == null) {
        return;
      }
      System.out.print("H");
      System.out.print("e");
      System.out.print("l");
      System.out.print("l");
      System.out.print("o");
      System.out.print(" ");
      System.out.print("w");
      System.out.print("o");
      System.out.print("r");
      System.out.print("l");
      System.out.print("d");
      System.out.println("!");
    }
  }

  enum EnumUnboxingCandidate {
    A,
    B;
  }
}
