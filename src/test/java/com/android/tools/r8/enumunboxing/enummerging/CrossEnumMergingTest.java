// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.enumunboxing.enummerging;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.enumunboxing.EnumUnboxingTestBase;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CrossEnumMergingTest extends EnumUnboxingTestBase {

  private final TestParameters parameters;
  private final boolean enumValueOptimization;
  private final EnumKeepRules enumKeepRules;

  @Parameters(name = "{0} valueOpt: {1} keep: {2}")
  public static List<Object[]> data() {
    return enumUnboxingTestParameters();
  }

  public CrossEnumMergingTest(
      TestParameters parameters, boolean enumValueOptimization, EnumKeepRules enumKeepRules) {
    this.parameters = parameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
  }

  @Test
  public void testEnumUnboxing() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(CrossEnumMergingTest.class)
        .addKeepMainRule(Main.class)
        .addKeepRules(enumKeepRules.getKeepRules())
        .addEnumUnboxingInspector(
            inspector -> inspector.assertUnboxed(TestEnumReturn.class, TestEnumArg.class))
        .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("TEST2", "TEST1", "1", "2", "2", "3");
  }

  public enum TestEnumReturn {
    TEST1 {
      @Override
      public TestEnumReturn other() {
        return TEST2;
      }
    },
    TEST2 {
      @Override
      public TestEnumReturn other() {
        return TEST1;
      }
    };

    public abstract TestEnumReturn other();
  }

  public enum TestEnumArg {
    TEST1 {
      @Override
      public void print(TestEnumArg arg) {
        System.out.println(arg.ordinal() + 1);
      }
    },
    TEST2 {
      @Override
      public void print(TestEnumArg arg) {
        System.out.println(arg.ordinal() + 2);
      }
    };

    public abstract void print(TestEnumArg arg);
  }

  static class Main {

    public static void main(String[] args) {
      System.out.println(TestEnumReturn.TEST1.other().toString());
      System.out.println(TestEnumReturn.TEST2.other().toString());

      TestEnumArg.TEST1.print(TestEnumArg.TEST1);
      TestEnumArg.TEST1.print(TestEnumArg.TEST2);
      TestEnumArg.TEST2.print(TestEnumArg.TEST1);
      TestEnumArg.TEST2.print(TestEnumArg.TEST2);
    }
  }
}
