// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.enumunboxing;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ValueOfWithoutCastEnumUnboxingTest extends EnumUnboxingTestBase {

  private final TestParameters parameters;
  private final boolean enumValueOptimization;
  private final EnumKeepRules enumKeepRules;

  @Parameters(name = "{0} valueOpt: {1} keep: {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        BooleanUtils.values(),
        getStudioEnumKeepRules());
  }

  public ValueOfWithoutCastEnumUnboxingTest(
      TestParameters parameters, boolean enumValueOptimization, EnumKeepRules enumKeepRules) {
    this.parameters = parameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
  }

  @Test
  public void testEnumUnboxing() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepClassAndMembersRules(Main.class)
        .addKeepRules(enumKeepRules.getKeepRules())
        .addEnumUnboxingInspector(inspector -> inspector.assertNotUnboxed(MyEnum.class))
        .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A");
  }

  static class Main {

    public static void main(String[] args) {
      MyEnum e = System.currentTimeMillis() > 0 ? MyEnum.A : MyEnum.B;
      // When the library method optimizer runs, the class argument to Enum.valueOf is still not a
      // const-class. Therefore, the library method optimizer cannot insert an assume-dynamic-type
      // instruction for the out-value of the call to Enum.valueOf. The argument is optimized into a
      // const-class before the enum unboxing analysis runs. The enum unboxer must conclude that the
      // enum is not subject to unboxing.
      Object o = Enum.valueOf(new ClassInlineCandidate().set(MyEnum.class).get(), e.name());
      escape(o);
    }

    // @Keep
    static void escape(Object o) {
      System.out.println(o);
    }
  }

  static class ClassInlineCandidate {

    Class<MyEnum> clazz;

    @NeverInline
    ClassInlineCandidate set(Class<MyEnum> clazz) {
      this.clazz = clazz;
      return this;
    }

    @NeverInline
    Class<MyEnum> get() {
      return clazz;
    }
  }

  enum MyEnum {
    A,
    B
  }
}
