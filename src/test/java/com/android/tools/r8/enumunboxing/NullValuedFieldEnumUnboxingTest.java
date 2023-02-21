// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.enumunboxing;

import com.android.tools.r8.TestParameters;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NullValuedFieldEnumUnboxingTest extends EnumUnboxingTestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public EnumKeepRules enumKeepRules;

  @Parameters(name = "{0}, keep: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), getAllEnumKeepRules());
  }

  @Test
  public void testEnumUnboxing() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(NullValuedFieldEnumUnboxingTest.class)
        .addKeepMainRule(Main.class)
        .addKeepRules(enumKeepRules.getKeepRules())
        .addEnumUnboxingInspector(inspector -> inspector.assertUnboxed(MyEnum.class))
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("a", "null");
  }

  enum MyEnum {
    A("a"),
    B(null);

    String value;

    MyEnum(String value) {
      this.value = value;
    }
  }

  static class Main {

    public static void main(String[] args) {
      MyEnum a = System.currentTimeMillis() > 0 ? MyEnum.A : MyEnum.B;
      MyEnum b = System.currentTimeMillis() > 0 ? MyEnum.B : MyEnum.A;
      System.out.println(a.value);
      System.out.println(b.value);
    }
  }
}
