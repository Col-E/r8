// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
//  for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.enumunboxing;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.TestParameters;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ValueOfEnumUnboxingFailureTest extends EnumUnboxingTestBase {

  private final TestParameters parameters;
  private final boolean enumValueOptimization;
  private final EnumKeepRules enumKeepRules;

  @Parameterized.Parameters(name = "{0} valueOpt: {1} keep: {2}")
  public static List<Object[]> data() {
    return enumUnboxingTestParameters();
  }

  public ValueOfEnumUnboxingFailureTest(
      TestParameters parameters, boolean enumValueOptimization, EnumKeepRules enumKeepRules) {
    this.parameters = parameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
  }

  @Test
  public void testEnumUnboxing() throws Exception {
    Class<?> success = Main.class;
    testForR8(parameters.getBackend())
        .addInnerClasses(ValueOfEnumUnboxingFailureTest.class)
        .addKeepMainRule(success)
        .enableNeverClassInliningAnnotations()
        .addKeepRules(enumKeepRules.getKeepRules())
        .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
        .allowDiagnosticInfoMessages()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspectDiagnosticMessages(
            m -> assertEnumIsBoxed(success.getDeclaredClasses()[0], success.getSimpleName(), m))
        .run(parameters.getRuntime(), success)
        .assertSuccessWithOutput("VALUE1");
  }

  static class Main {

    @NeverClassInline
    enum Enum {
      VALUE1,
      VALUE2
    }

    public static void main(String[] args) {
      System.out.print(java.lang.Enum.valueOf(Enum.class, "VALUE1"));
    }
  }
}
