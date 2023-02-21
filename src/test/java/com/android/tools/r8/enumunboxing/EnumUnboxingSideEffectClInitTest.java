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
public class EnumUnboxingSideEffectClInitTest extends EnumUnboxingTestBase {
  private static final Class<MyEnum> ENUM_CLASS = MyEnum.class;
  private final TestParameters parameters;
  private final boolean enumValueOptimization;
  private final EnumKeepRules enumKeepRules;

  @Parameters(name = "{0} valueOpt: {1} keep: {2}")
  public static List<Object[]> data() {
    return enumUnboxingTestParameters();
  }

  public EnumUnboxingSideEffectClInitTest(
      TestParameters parameters, boolean enumValueOptimization, EnumKeepRules enumKeepRules) {
    this.parameters = parameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
  }

  @Test
  public void testEnumUnboxing() throws Exception {
    Class<?> classToTest = MainEnum.class;
    testForR8(parameters.getBackend())
        .addInnerClasses(EnumUnboxingSideEffectClInitTest.class)
        .addKeepMainRule(classToTest)
        .addKeepRules(enumKeepRules.getKeepRules())
        .addEnumUnboxingInspector(inspector -> inspector.assertUnboxed(ENUM_CLASS))
        .enableNeverClassInliningAnnotations()
        .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), classToTest)
        .assertSuccessWithOutputLines("0");
  }

  @NeverClassInline
  enum MyEnum {
    A,
    B
  }

  @NeverClassInline
  enum MainEnum {
    INSTANCE;
    // The clinit of this enum needs to be reprocessed by the enum unboxer to rewrite MyEnum.a
    // and the static put instruction to the new field.
    static MyEnum e = System.currentTimeMillis() > 0 ? MyEnum.A : MyEnum.B;

    public static void main(String[] args) {
      System.out.println(e.ordinal());
    }
  }
}
