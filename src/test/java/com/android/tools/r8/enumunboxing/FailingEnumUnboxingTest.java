// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.enumunboxing;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestParameters;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class FailingEnumUnboxingTest extends EnumUnboxingTestBase {

  private final TestParameters parameters;
  private final boolean enumValueOptimization;
  private final EnumKeepRules enumKeepRules;

  @Parameters(name = "{0} valueOpt: {1} keep: {2}")
  public static List<Object[]> data() {
    return enumUnboxingTestParameters();
  }

  public FailingEnumUnboxingTest(
      TestParameters parameters, boolean enumValueOptimization, EnumKeepRules enumKeepRules) {
    this.parameters = parameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
  }

  @Test
  public void testEnumUnboxingFailure() throws Exception {
    R8TestRunResult run =
        testForR8(parameters.getBackend())
            .addInnerClasses(FailingEnumUnboxingTest.class)
            .addKeepMainRule(EnumStaticFieldMain.class)
            .enableNeverClassInliningAnnotations()
            .addKeepRules(enumKeepRules.getKeepRules())
            .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
            .allowDiagnosticInfoMessages()
            .setMinApi(parameters.getApiLevel())
            .compile()
            .inspectDiagnosticMessages(
                m ->
                    assertEnumIsBoxed(
                        EnumStaticFieldMain.EnumStaticField.class,
                        EnumStaticFieldMain.class.getSimpleName(),
                        m))
            .run(parameters.getRuntime(), EnumStaticFieldMain.class)
            .assertSuccess();
    assertLines2By2Correct(run.getStdOut());
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
