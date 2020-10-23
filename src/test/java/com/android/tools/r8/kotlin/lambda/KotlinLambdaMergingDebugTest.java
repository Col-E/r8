// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.lambda;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticException;
import static org.junit.Assert.assertThrows;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import com.android.tools.r8.kotlin.AbstractR8KotlinTestBase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KotlinLambdaMergingDebugTest extends AbstractR8KotlinTestBase {

  private final TestParameters parameters;
  private static final String FOLDER = "reprocess_merged_lambdas_kstyle";
  private static final String MAIN_CLASS = "reprocess_merged_lambdas_kstyle.MainKt";

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public KotlinLambdaMergingDebugTest(TestParameters parameters) {
    super(KotlinTargetVersion.JAVA_6);
    this.parameters = parameters;
  }

  @Test
  public void testMergingKStyleLambdasAndReprocessingInDebug() {
    assertThrows(
        CompilationFailedException.class,
        () -> {
          testForR8(parameters.getBackend())
              .setMode(CompilationMode.DEBUG)
              .addProgramFiles(getKotlinJarFile(FOLDER))
              .addProgramFiles(getJavaJarFile(FOLDER))
              .setMinApi(parameters.getApiLevel())
              .addKeepMainRule(MAIN_CLASS)
              .allowDiagnosticWarningMessages()
              .compileWithExpectedDiagnostics(
                  diagnostics -> {
                    diagnostics.assertErrorsMatch(diagnosticException(AssertionError.class));
                  });
        });
  }
}
