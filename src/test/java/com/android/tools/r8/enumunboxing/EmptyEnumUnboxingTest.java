// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.enumunboxing;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EmptyEnumUnboxingTest extends EnumUnboxingTestBase {

  private final TestParameters parameters;
  private final boolean enumValueOptimization;
  private final EnumKeepRules enumKeepRules;

  @Parameters(name = "{0} valueOpt: {1} keep: {2}")
  public static List<Object[]> data() {
    return enumUnboxingTestParameters();
  }

  public EmptyEnumUnboxingTest(
      TestParameters parameters, boolean enumValueOptimization, EnumKeepRules enumKeepRules) {
    this.parameters = parameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
  }

  @Test
  public void testEnumUnboxing() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(EmptyEnumUnboxingTest.class)
        .addKeepMainRule(Main.class)
        .addKeepRules(enumKeepRules.getKeepRules())
        // TODO(b/166532373): Unbox enum with no cases.
        .addEnumUnboxingInspector(inspector -> inspector.assertNotUnboxed(MyEnum.class))
        .enableNeverClassInliningAnnotations()
        .enableInliningAnnotations()
        .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccess()
        .inspectStdOut(this::assertLines2By2Correct);
  }

  @NeverClassInline
  enum MyEnum {
    ;

    @NeverInline
    static void print() {
      System.out.println("PRINT");
    }
  }

  static class Main {

    public static void main(String[] args) {
      MyEnum.print();
      System.out.println("PRINT");
    }
  }
}
