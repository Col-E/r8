// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.internal;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.UnverifiableCfCodeDiagnostic;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class Regression127524985 extends TestBase {

  private static final String MAIN = "com.android.tools.r8.internal.Regression127524985$Main";

  private static final Path JAR =
      Paths.get(ToolHelper.THIRD_PARTY_DIR + "internal/issue-127524985/issue-127524985.jar");

  private static final String EXPECTED = StringUtils.lines("true");

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private final TestParameters parameters;

  public Regression127524985(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testJvm() throws Throwable {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addClasspath(JAR)
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testD8() throws Throwable {
    assumeTrue(parameters.isDexRuntime());
    testForD8()
        .addProgramFiles(JAR)
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testR8Cf() throws Throwable {
    assumeTrue(parameters.isCfRuntime());
    testForR8(parameters.getBackend())
        .debug()
        .noTreeShaking()
        .addDontObfuscate()
        .addKeepAllAttributes()
        .addKeepRules("-dontwarn")
        .allowDiagnosticWarningMessages()
        .addProgramFiles(JAR)
        .setMinApi(parameters)
        .compileWithExpectedDiagnostics(
            diagnostics ->
                diagnostics.assertWarningsMatch(
                    allOf(
                        diagnosticType(UnverifiableCfCodeDiagnostic.class),
                        diagnosticMessage(
                            containsString(
                                "Unverifiable code in `"
                                    + "void com.google.protobuf.contrib.android."
                                    + "ProtoParsers$InternalDontUse.<clinit>()`"))),
                    allOf(
                        diagnosticType(UnverifiableCfCodeDiagnostic.class),
                        diagnosticMessage(
                            containsString(
                                "Unverifiable code in `"
                                    + "void com.google.protobuf.contrib.android.ProtoParsers"
                                    + ".put(android.os.Bundle, java.lang.String, "
                                    + "com.google.protobuf.MessageLite)`")))))
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(EXPECTED);
  }
}
