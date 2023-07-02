// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.enumunboxing;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** This is a regression test for b/287193321. */
@RunWith(Parameterized.class)
public class EnumUnboxNullArgumentTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .addOptionsModification(options -> options.testing.disableLir())
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatThrows(NullPointerException.class);
  }

  public enum MyEnum {
    FOO("1"),
    BAR("2");

    final String value;

    MyEnum(String value) {
      this.value = value;
    }
  }

  public static class Main {

    public static void main(String[] args) {
      // Delay observing that arguments to bar is null until we've inlined foo() and getEnum().
      String foo = foo();
      String[] bar = bar(getEnum(), foo);
      // To ensure bar(MyEnum,String) is not inlined in the first round we add a few additional
      // calls that will be stripped during IR-processing of main.
      if (foo != null) {
        bar(MyEnum.FOO, foo);
        bar(MyEnum.BAR, foo);
      }
      for (String b : bar) {
        System.out.println(b);
      }
    }

    public static String[] bar(MyEnum myEnum, String foo) {
      if (System.currentTimeMillis() > 1) {
        // Ensure that the construction is in a separate block than entry() to have constant
        // canonicalization align the two null values into one.
        return new String[] {myEnum.value, foo};
      }
      return new String[] {};
    }

    public static MyEnum getEnum() {
      return null;
    }

    public static String foo() {
      return null;
    }
  }
}
