// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.enumunboxing;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** Regression test for b/236618700. */
@RunWith(Parameterized.class)
public class NullAssignmentToArrayTypeEnumUnboxingTest extends EnumUnboxingTestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean enumValueOptimization;

  @Parameter(2)
  public EnumKeepRules enumKeepRules;

  @Parameters(name = "{0}, value opt.: {1}, keep: {2}")
  public static List<Object[]> data() {
    return enumUnboxingTestParameters();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(NullAssignmentToArrayTypeEnumUnboxingTest.class)
        .addKeepMainRule(Main.class)
        .addKeepRules(enumKeepRules.getKeepRules())
        .addEnumUnboxingInspector(inspector -> inspector.assertUnboxed(MyEnum.class))
        .enableInliningAnnotations()
        .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject mainClassSubject = inspector.clazz(Main.class);
              assertThat(mainClassSubject, isPresent());

              MethodSubject clinitMethodSubject = mainClassSubject.clinit();
              assertThat(clinitMethodSubject, isPresent());
              assertTrue(
                  clinitMethodSubject
                      .streamInstructions()
                      .anyMatch(InstructionSubject::isFieldAccess));
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A");
  }

  static class Main {

    static MyEnum[] e;

    static {
      System.currentTimeMillis(); // To preserve the null assignment below.
      e = null;
    }

    public static void main(String[] args) {
      setField();
      getField();
    }

    @NeverInline
    static void setField() {
      e = new MyEnum[] {System.currentTimeMillis() > 0 ? MyEnum.A : MyEnum.B};
    }

    @NeverInline
    static void getField() {
      System.out.println(e[0].name());
    }
  }

  enum MyEnum {
    A,
    B
  }
}
