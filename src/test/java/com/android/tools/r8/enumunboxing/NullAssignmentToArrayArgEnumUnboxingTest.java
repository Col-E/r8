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
public class NullAssignmentToArrayArgEnumUnboxingTest extends EnumUnboxingTestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean enumValueOptimization;

  @Parameter(2)
  public EnumKeepRules enumKeepRules;

  @Parameters(name = "{0}, value opt.: {1}, keep: {2}")
  public static List<Object[]> data() {
    return enumUnboxingTestParameters();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepRules(enumKeepRules.getKeepRules())
        .addEnumUnboxingInspector(inspector -> inspector.assertUnboxed(MyEnum.class))
        // We need to disable entirely inlining since not only the methods checkNotNull and
        // contains should not be inlined, but also the synthetic method with the zero check
        // replacing the checkNotNull method should not be inlined.
        .addOptionsModification(opt -> opt.inlinerOptions().enableInlining = false)
        .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
        .setMinApi(parameters.getApiLevel())
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("false", "true", "npe", "npe");
  }

  static class Main {

    public static void main(String[] args) {
      System.out.println(contains(MyEnum.A, new MyEnum[] {MyEnum.B, MyEnum.C}));
      System.out.println(contains(MyEnum.B, new MyEnum[] {MyEnum.B, MyEnum.C}));
      try {
        System.out.println(contains(MyEnum.B, null));
      } catch (NullPointerException npe) {
        System.out.println("npe");
      }
      try {
        System.out.println(contains(null, new MyEnum[] {MyEnum.B, MyEnum.C}));
      } catch (NullPointerException npe) {
        System.out.println("npe");
      }
    }

    static void checkNotNull(Object o, String msg) {
      if (o == null) {
        throw new NullPointerException(msg);
      }
    }

    static boolean contains(MyEnum e, MyEnum[] contents) {
      checkNotNull(e, "elem");
      checkNotNull(contents, "array");
      for (MyEnum content : contents) {
        if (content == e) {
          return true;
        }
      }
      return false;
    }
  }

  enum MyEnum {
    A,
    B,
    C;
  }
}
