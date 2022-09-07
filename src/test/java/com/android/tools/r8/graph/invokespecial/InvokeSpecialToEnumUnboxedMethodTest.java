// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.invokespecial;

import static com.android.tools.r8.graph.invokespecial.InvokeSpecialToEnumUnboxedMethodTest.MyEnum.TEST_1;
import static com.android.tools.r8.graph.invokespecial.InvokeSpecialToEnumUnboxedMethodTest.MyEnum.TEST_2;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InvokeSpecialToEnumUnboxedMethodTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withCfRuntimes().build();
  }

  @Test
  public void testR8() throws Exception {
    testForR8Compat(parameters.getBackend())
        .addInnerClasses(getClass())
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Foo");
  }

  public enum MyEnum {
    TEST_1("Foo"),
    TEST_2("Bar");

    private final String str;

    MyEnum(String str) {
      this.str = str;
    }

    @NeverInline
    private String getStr() {
      return str;
    }
  }

  // This is similar to PrivateEnumWithPrivateInvokeSpecialTest by invoking a private method on an
  // enum that is unboxed.
  public static class Main {

    public static void main(String[] args) {
      System.out.println((args.length == 0 ? TEST_1 : TEST_2).getStr());
    }
  }
}
