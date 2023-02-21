// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.enumunboxing;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.enumunboxing.PinnedEnumUnboxingTest.MainWithKeptEnum.MyEnum;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PinnedEnumUnboxingTest extends EnumUnboxingTestBase {

  private static final Class<?>[] TESTS = {MainWithKeptEnum.class, MainWithKeptEnumArray.class};

  private final TestParameters parameters;
  private final boolean enumValueOptimization;
  private final EnumKeepRules enumKeepRules;

  @Parameters(name = "{0} valueOpt: {1} keep: {2}")
  public static List<Object[]> data() {
    return enumUnboxingTestParameters();
  }

  public PinnedEnumUnboxingTest(
      TestParameters parameters, boolean enumValueOptimization, EnumKeepRules enumKeepRules) {
    this.parameters = parameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
  }

  @Test
  public void testEnumUnboxing() throws Exception {
    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addInnerClasses(PinnedEnumUnboxingTest.class)
            .addKeepMainRules(TESTS)
            .addKeepClassRules(MyEnum.class)
            .addKeepMethodRules(MainWithKeptEnumArray.class, "keptMethod()")
            .addKeepRules(enumKeepRules.getKeepRules())
            .addEnumUnboxingInspector(
                inspector ->
                    inspector.assertNotUnboxed(
                        MainWithKeptEnum.MyEnum.class, MainWithKeptEnumArray.MyEnum.class))
            .enableNeverClassInliningAnnotations()
            .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
            .setMinApi(parameters)
            .compile();
    for (Class<?> main : TESTS) {
      compileResult.run(parameters.getRuntime(), main).assertSuccessWithOutputLines("0");
    }
  }

  static class MainWithKeptEnum {

    @NeverClassInline
    enum MyEnum {
      A,
      B,
      C
    }

    public static void main(String[] args) {
      System.out.println(MyEnum.A.ordinal());
    }
  }

  static class MainWithKeptEnumArray {
    @NeverClassInline
    enum MyEnum {
      A,
      B,
      C
    }

    public static void main(String[] args) {
      System.out.println(MyEnum.A.ordinal());
    }

    public static MyEnum[] keptMethod() {
      System.out.println("KEPT");
      return null;
    }
  }
}
