// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.identifiernamestring;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static org.hamcrest.CoreMatchers.equalTo;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class IdentifierNameStringStaticFinalFieldTest extends TestBase {

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
        .addKeepClassAndMembersRules(Main.class)
        .addKeepRules("-identifiernamestring class * { java.lang.String CLASS_NAME; }")
        .allowDiagnosticWarningMessages()
        .setMinApi(AndroidApiLevel.B)
        .compileWithExpectedDiagnostics(
            diagnostics ->
                diagnostics.assertWarningsMatch(
                    diagnosticMessage(
                        equalTo(
                            StringUtils.joinLines(
                                "Rule matches the static final field `java.lang.String "
                                    + Main.class.getTypeName()
                                    + ".CLASS_NAME`, which may have been inlined:"
                                    + " -identifiernamestring class * {",
                                "  java.lang.String CLASS_NAME;",
                                "}")))));
  }

  static class Main {

    // @IdentifierNameString
    public static final String CLASS_NAME = "Foo";

    public static void main(String[] args) {}
  }
}
