// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.switches;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SwitchMapWithSubtypesTest extends TestBase {

  private final TestParameters parameters;
  private static final String[] EXPECTED_RESULT = {
    "ta", "SB", "tb", "SC", "C", "D", "tatrue", "SB", "tbtrue", "SC", "Cfalse", "Dfalse"
  };

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public SwitchMapWithSubtypesTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    R8TestCompileResult compile =
        testForR8(parameters.getBackend())
            .addInnerClasses(SwitchMapWithSubtypesTest.class)
            .addKeepMainRule(TestClass.class)
            .addDontObfuscate()
            .enableInliningAnnotations()
            .setMinApi(parameters)
            .compile();
    compile
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines(EXPECTED_RESULT);
  }

  static class TestClass {
    public static void main(String[] args) {
      switchMethod();
      switchMethodSubtype();
    }

    @NeverInline
    private static void switchMethod() {
      for (MyEnum value : MyEnum.values()) {
        switch (value) {
          case B:
            System.out.println("SB");
            break;
          case C:
            System.out.println("SC");
            break;
        }
        System.out.println(value.toString());
      }
    }

    @NeverInline
    private static void switchMethodSubtype() {
      for (MyEnumSubtype value : MyEnumSubtype.values()) {
        switch (value) {
          case B:
            System.out.println("SB");
            break;
          case C:
            System.out.println("SC");
            break;
        }
        System.out.println(value.toString() + value.subType);
      }
    }
  }

  enum MyEnum {
    A {
      @Override
      public String toString() {
        return "ta";
      }
    },
    B {
      @Override
      public String toString() {
        return "tb";
      }
    },
    C,
    D;
  }

  enum MyEnumSubtype {
    A(true) {
      @Override
      public String toString() {
        return "ta";
      }
    },
    B(true) {
      @Override
      public String toString() {
        return "tb";
      }
    },
    C(false),
    D(false);

    final boolean subType;

    MyEnumSubtype(boolean fieldSet) {
      this.subType = fieldSet;
    }
  }
}
