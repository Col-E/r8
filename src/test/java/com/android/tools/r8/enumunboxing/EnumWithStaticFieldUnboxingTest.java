// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.enumunboxing;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.TestParameters;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EnumWithStaticFieldUnboxingTest extends EnumUnboxingTestBase {

  private final TestParameters parameters;
  private final boolean enumValueOptimization;
  private final EnumKeepRules enumKeepRules;

  @Parameters(name = "{0} valueOpt: {1} keep: {2}")
  public static List<Object[]> data() {
    return enumUnboxingTestParameters();
  }

  public EnumWithStaticFieldUnboxingTest(
      TestParameters parameters, boolean enumValueOptimization, EnumKeepRules enumKeepRules) {
    this.parameters = parameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
  }

  @Test
  public void testEnumUnboxingFailure() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(EnumWithStaticFieldUnboxingTest.class)
        .addKeepMainRule(EnumStaticFieldMain.class)
        .enableNeverClassInliningAnnotations()
        .addKeepRules(enumKeepRules.getKeepRules())
        .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
        .addEnumUnboxingInspector(
            inspector -> inspector.assertUnboxed(EnumStaticFieldMain.EnumStaticField.class))
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), EnumStaticFieldMain.class)
        .assertSuccess()
        .inspectStdOut(this::assertLines2By2Correct);
  }

  static class EnumStaticFieldMain {

    public static void main(String[] args) {
      System.out.println(EnumStaticField.A.ordinal());
      System.out.println(0);
      System.out.println(EnumStaticField.X.ordinal());
      System.out.println(0);
    }

    @NeverClassInline
    enum EnumStaticField {
      A,
      B,
      C;
      static EnumStaticField X = A;
    }
  }
}
