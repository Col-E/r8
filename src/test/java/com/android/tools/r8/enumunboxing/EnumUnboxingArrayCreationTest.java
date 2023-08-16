// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
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
public class EnumUnboxingArrayCreationTest extends EnumUnboxingTestBase {

  private final TestParameters parameters;
  private final boolean enumValueOptimization;
  private final EnumKeepRules enumKeepRules;

  @Parameters(name = "{0} valueOpt: {1} keep: {2}")
  public static List<Object[]> data() {
    return enumUnboxingTestParameters();
  }

  public EnumUnboxingArrayCreationTest(
      TestParameters parameters, boolean enumValueOptimization, EnumKeepRules enumKeepRules) {
    this.parameters = parameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
  }

  @Test
  public void testEnumUnboxing() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRules(ArrayCreation.class)
        .enableNeverClassInliningAnnotations()
        .addKeepRules(enumKeepRules.getKeepRules())
        .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
        .addEnumUnboxingInspector(inspector -> inspector.assertUnboxed(ArrayCreation.MyEnum.class))
        .setMinApi(parameters)
        .run(parameters.getRuntime(), ArrayCreation.class)
        .assertSuccessWithOutputLines("true", "false", "true", "false", "true");
  }

  public static class ArrayCreation {

    public static void main(String[] args) {
      MyEnum[] myEnums = {null, MyEnum.A, null, MyEnum.B, null};
      System.out.println(myEnums[0] == null);
      System.out.println(myEnums[1] == null);
      System.out.println(myEnums[2] == null);
      System.out.println(myEnums[3] == null);
      System.out.println(myEnums[4] == null);
    }

    @NeverClassInline
    enum MyEnum {
      A,
      B;
    }
  }
}
