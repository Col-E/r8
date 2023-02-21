// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.enumunboxing;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.equalTo;

import com.android.tools.r8.CheckEnumUnboxed;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.errors.CheckEnumUnboxedDiagnostic;
import com.android.tools.r8.utils.codeinspector.AssertUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CheckEnumUnboxedTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    AssertUtils.assertFailsCompilation(
        () ->
            testForR8(parameters.getBackend())
                .addInnerClasses(getClass())
                .addKeepMainRule(Main.class)
                .enableCheckEnumUnboxedAnnotations()
                .setMinApi(parameters)
                .compileWithExpectedDiagnostics(
                    diagnostics -> {
                      diagnostics.assertErrorsMatch(
                          allOf(
                              diagnosticType(CheckEnumUnboxedDiagnostic.class),
                              diagnosticMessage(
                                  equalTo(
                                      "Enum unboxing checks failed."
                                          + System.lineSeparator()
                                          + "Enum "
                                          + EscapingEnum.class.getTypeName()
                                          + " was not unboxed."))));
                    }));
  }

  static class Main {

    public static void main(String[] args) {
      EscapingEnum escapingEnum = System.currentTimeMillis() > 0 ? EscapingEnum.A : EscapingEnum.B;
      System.out.println(escapingEnum);
      UnboxedEnum unboxedEnum = System.currentTimeMillis() > 0 ? UnboxedEnum.A : UnboxedEnum.B;
      System.out.println(unboxedEnum.ordinal());
    }
  }

  @CheckEnumUnboxed
  enum EscapingEnum {
    A,
    B
  }

  @CheckEnumUnboxed
  enum UnboxedEnum {
    A,
    B
  }
}
