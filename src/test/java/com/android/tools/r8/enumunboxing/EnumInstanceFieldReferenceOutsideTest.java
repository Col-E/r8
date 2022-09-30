// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
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

/**
 * This is a variant of a regression test for b/247146910 where the instance field on the enum is
 * unrelated to enum unboxing.
 */
@RunWith(Parameterized.class)
public class EnumInstanceFieldReferenceOutsideTest extends EnumUnboxingTestBase {

  private final TestParameters parameters;
  private final boolean enumValueOptimization;
  private final EnumKeepRules enumKeepRules;

  @Parameters(name = "{0} valueOpt: {1} keep: {2}")
  public static List<Object[]> data() {
    return enumUnboxingTestParameters();
  }

  public EnumInstanceFieldReferenceOutsideTest(
      TestParameters parameters, boolean enumValueOptimization, EnumKeepRules enumKeepRules) {
    this.parameters = parameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addInnerClasses(EnumInstanceFieldReferenceOutsideTest.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("42");
  }

  @Test
  public void testEnumUnboxingAllowNotPrunedEnums() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(EnumInstanceFieldReferenceOutsideTest.class)
        .addKeepMainRule(Main.class)
        .addKeepRules(enumKeepRules.getKeepRules())
        .addEnumUnboxingInspector(inspector -> inspector.assertNotUnboxed(MyEnum.class))
        .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
        .addNeverClassInliningAnnotations()
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("42");
  }

  @NeverClassInline
  public enum MyEnum {
    A,
    B;

    public int instanceValue = 1;
  }

  public static class Main {

    public static void main(String[] args) throws Exception {
      set(System.currentTimeMillis() > 0 ? 42 : 0);
      System.out.println(MyEnum.B.instanceValue);
    }

    public static void set(int newValue) {
      MyEnum.B.instanceValue = newValue;
    }
  }
}
