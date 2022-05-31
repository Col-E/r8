// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.assumevalues;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.equalTo;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.errors.AssumeValuesMissingStaticFieldDiagnostic;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AssumeValuesMissingStaticFieldDiagnosticTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(Backend.DEX)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepRules(
            "-assumevalues class " + Main.class.getTypeName() + " {",
            "  static java.lang.Object get() return Missing.field;",
            "}")
        .allowDiagnosticWarningMessages()
        .setMinApi(AndroidApiLevel.LATEST)
        .compileWithExpectedDiagnostics(
            diagnostics ->
                diagnostics.assertWarningsMatch(
                    allOf(
                        diagnosticType(AssumeValuesMissingStaticFieldDiagnostic.class),
                        diagnosticMessage(
                            equalTo(
                                "The field Missing.field is used as the return value in an "
                                    + "-assumenosideeffects or -assumevalues rule, but no such "
                                    + "static field exists.")))));
  }

  static class Main {

    static Object get() {
      return null;
    }
  }
}
