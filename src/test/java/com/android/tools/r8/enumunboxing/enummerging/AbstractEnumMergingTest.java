// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.enumunboxing.enummerging;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.enumunboxing.EnumUnboxingTestBase;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AbstractEnumMergingTest extends EnumUnboxingTestBase {

  private final TestParameters parameters;
  private final boolean enumValueOptimization;
  private final EnumKeepRules enumKeepRules;

  @Parameters(name = "{0} valueOpt: {1} keep: {2}")
  public static List<Object[]> data() {
    return enumUnboxingTestParameters();
  }

  public AbstractEnumMergingTest(
      TestParameters parameters, boolean enumValueOptimization, EnumKeepRules enumKeepRules) {
    this.parameters = parameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
  }

  @Test
  public void testEnumUnboxing() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepRules(enumKeepRules.getKeepRules())
        .addOptionsModification(opt -> opt.testing.enableEnumWithSubtypesUnboxing = true)
        .addEnumUnboxingInspector(inspector -> inspector.assertUnboxed(MyEnum.class))
        .enableInliningAnnotations()
        .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("336", "74", "96", "44");
  }

  enum MyEnum {
    A(8) {
      @NeverInline
      @Override
      public long operate(long another) {
        return num * another;
      }
    },
    B(32) {
      @NeverInline
      @Override
      public long operate(long another) {
        return num + another;
      }
    };
    final long num;

    MyEnum(long num) {
      this.num = num;
    }

    public abstract long operate(long another);
  }

  static class Main {

    public static void main(String[] args) {
      System.out.println(MyEnum.A.operate(42));
      System.out.println(MyEnum.B.operate(42));
      System.out.println(indirect(MyEnum.A));
      System.out.println(indirect(MyEnum.B));
    }

    @NeverInline
    public static long indirect(MyEnum e) {
      return e.operate(12);
    }
  }
}
