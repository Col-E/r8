// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
//  for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.rewrite.enums;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class EnumValueOfOptimizationTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public EnumValueOfOptimizationTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testValueOf() throws Exception {
    testForR8(parameters.getBackend())
        .addKeepMainRule(Main.class)
        .addInnerClasses(EnumValueOfOptimizationTest.class)
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("npe OK", "iae1 OK", "iae2 OK", "iae3 OK", "iae4 OK");
  }

  enum MyEnum {
    A,
    B
  }

  enum ComplexEnum {
    A {
      @Override
      public String toString() {
        return "a0";
      }
    },
    B
  }

  @SuppressWarnings({"unchecked", "ConstantConditions"})
  static class Main {
    public static void main(String[] args) {
      myEnumTest();
      complexEnumTest();
    }

    private static void complexEnumTest() {
      Enum<?> e = null;
      try {
        e = subtypeError();
        System.out.println("iae4 KO");
      } catch (IllegalArgumentException iae) {
        System.out.println("iae4 OK");
      }
      if (e != null) {
        throw new Error("enum set");
      }
    }

    private static Enum<?> subtypeError() {
      return Enum.valueOf((Class) ComplexEnum.A.getClass(), "A");
    }

    private static void myEnumTest() {
      Enum<?> e = null;
      try {
        e = nullClassError();
        System.out.println("npe KO");
      } catch (NullPointerException ignored) {
        System.out.println("npe OK");
      }
      if (e != null) {
        throw new Error("enum set");
      }
      try {
        e = invalidNameError();
        System.out.println("iae1 KO");
      } catch (IllegalArgumentException iae) {
        System.out.println("iae1 OK");
      }
      if (e != null) {
        throw new Error("enum set");
      }
      try {
        e = enumClassError();
        System.out.println("iae2 KO");
      } catch (IllegalArgumentException iae) {
        System.out.println("iae2 OK");
      }
      if (e != null) {
        throw new Error("enum set");
      }
      try {
        e = voidError();
        System.out.println("iae3 KO");
      } catch (IllegalArgumentException iae) {
        System.out.println("iae3 OK");
      }
      if (e != null) {
        throw new Error("enum set");
      }
    }

    private static Enum<?> voidError() {
      return Enum.valueOf((Class) Void.class, "TYPE");
    }

    private static Enum<?> enumClassError() {
      return Enum.valueOf(Enum.class, "smth");
    }

    private static Enum<?> invalidNameError() {
      return Enum.valueOf(MyEnum.class, "curly");
    }

    private static Enum<?> nullClassError() {
      return Enum.valueOf((Class<MyEnum>) null, "a string");
    }
  }
}
