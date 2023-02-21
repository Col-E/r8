// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.enumunboxing;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EnumWithCheckNotNullUserUnboxingTest extends EnumUnboxingTestBase {

  @Parameter(0)
  public EnumKeepRules enumKeepRules;

  @Parameter(1)
  public TestParameters parameters;

  @Parameters(name = "{1}, keep: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        getAllEnumKeepRules(), getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepRules(enumKeepRules.getKeepRules())
        .addEnumUnboxingInspector(inspector -> inspector.assertUnboxed(MyEnum.class))
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(
            "Passed null check (parameter args)",
            "Passed null check (variable e)",
            "Passed null check (variable e)",
            "Passed null check (variable e)");
  }

  static class Main {
    public static void main(String[] args) {
      Checker.checkNotNull(args, "parameter args");
      for (MyEnum e : MyEnum.values()) {
        Checker.checkNotNull(e, "variable e");
      }
    }
  }

  enum MyEnum {
    A,
    B,
    C
  }

  static class Checker {

    @NeverInline
    static <T> T checkNotNull(T t, String msg) {
      if (t != null) {
        System.out.println("Passed null check (" + msg + ")");
        return t;
      }
      System.out.println("Expected non-null, got null (" + msg + ")");
      throw new RuntimeException();
    }
  }
}
