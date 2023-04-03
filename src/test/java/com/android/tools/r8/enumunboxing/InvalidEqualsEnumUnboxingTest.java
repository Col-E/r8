// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.enumunboxing;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.codeinspector.EnumUnboxingInspector;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InvalidEqualsEnumUnboxingTest extends EnumUnboxingTestBase {

  private final TestParameters parameters;
  private final boolean enumValueOptimization;
  private final EnumKeepRules enumKeepRules;

  @Parameters(name = "{0} valueOpt: {1} keep: {2}")
  public static List<Object[]> data() {
    return enumUnboxingTestParameters();
  }

  public InvalidEqualsEnumUnboxingTest(
      TestParameters parameters, boolean enumValueOptimization, EnumKeepRules enumKeepRules) {
    this.parameters = parameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
  }

  @Test
  public void testEnumUnboxing() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(EnumEquals.class)
        .addEnumUnboxingInspector(EnumUnboxingInspector::assertNoEnumsUnboxed)
        .enableNeverClassInliningAnnotations()
        .addKeepRules(enumKeepRules.getKeepRules())
        .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
        .setMinApi(parameters)
        .run(parameters.getRuntime(), EnumEquals.class)
        .assertSuccessWithOutputLines("false", "false");
  }

  static class EnumEquals {

    @NeverClassInline
    enum MyEnumEquals {
      A,
      B
    }

    @NeverClassInline
    enum MyEnumEquals1 {
      A,
      B
    }

    @NeverClassInline
    enum MyEnumEquals2 {
      A,
      B
    }

    public static void main(String[] args) {
      Object guineaPig = new Object();
      System.out.println(MyEnumEquals.A.equals(guineaPig));
      System.out.println(MyEnumEquals1.A.equals(MyEnumEquals2.A));
    }
  }
}
