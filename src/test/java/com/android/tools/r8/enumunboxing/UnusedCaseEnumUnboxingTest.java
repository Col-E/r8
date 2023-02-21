// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.enumunboxing;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class UnusedCaseEnumUnboxingTest extends EnumUnboxingTestBase {
  private final TestParameters parameters;
  private final boolean enumValueOptimization;
  private final EnumKeepRules enumKeepRules;

  @Parameterized.Parameters(name = "{0} valueOpt: {1} keep: {2}")
  public static List<Object[]> data() {
    return enumUnboxingTestParameters();
  }

  public UnusedCaseEnumUnboxingTest(
      TestParameters parameters, boolean enumValueOptimization, EnumKeepRules enumKeepRules) {
    this.parameters = parameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
  }

  @Test
  public void testEnumUnboxing() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(UnusedCaseEnumUnboxingTest.class)
        .addKeepMainRule(Main.class)
        .addEnumUnboxingInspector(inspector -> inspector.assertUnboxed(MyEnum.class))
        .enableNeverClassInliningAnnotations()
        .enableInliningAnnotations()
        .addKeepRules(enumKeepRules.getKeepRules())
        .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
        .setMinApi(parameters)
        .compile()
        .inspect(this::assertFieldsRemoved)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccess()
        .inspectStdOut(this::assertLines2By2Correct);
  }

  private void assertFieldsRemoved(CodeInspector codeInspector) {
    codeInspector.clazz(Main.class);
  }

  @NeverClassInline
  enum MyEnum {
    USED1("used1"),
    UNUSED1("unused1"),
    USED2("used2"),
    UNUSED2("unused2");

    final String myField;

    MyEnum(String data) {
      this.myField = data;
    }
  }

  static class Main {

    public static void main(String[] args) {
      printEnumField(MyEnum.USED1);
      System.out.println("used1");
      printEnumField(MyEnum.USED2);
      System.out.println("used2");

      printOrdinal(MyEnum.USED1);
      System.out.println("0");
      printOrdinal(MyEnum.USED2);
      System.out.println("2");
    }

    @NeverInline
    private static void printEnumField(MyEnum e) {
      System.out.println(e.myField);
    }

    @NeverInline
    private static void printOrdinal(MyEnum e) {
      System.out.println(e.ordinal());
    }
  }
}
