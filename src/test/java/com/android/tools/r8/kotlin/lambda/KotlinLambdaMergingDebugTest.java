// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.lambda;

import static org.hamcrest.CoreMatchers.equalTo;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.KotlinTestBase;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KotlinLambdaMergingDebugTest extends KotlinTestBase {

  private final TestParameters parameters;
  private static final String FOLDER = "reprocess_merged_lambdas_kstyle";
  private static final String MAIN_CLASS = "reprocess_merged_lambdas_kstyle.MainKt";

  @Parameters(name = "{0}, {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(),
        getKotlinTestParameters().withAllCompilersAndTargetVersions().build());
  }

  public KotlinLambdaMergingDebugTest(
      TestParameters parameters, KotlinTestParameters kotlinParameters) {
    super(kotlinParameters);
    this.parameters = parameters;
  }

  private static final KotlinCompileMemoizer compiledJars =
      getCompileMemoizer(getKotlinFilesInResource(FOLDER), FOLDER)
          .configure(kotlinCompilerTool -> kotlinCompilerTool.includeRuntime().noReflect());

  @Test
  public void testMergingKStyleLambdasAndReprocessingInDebug() throws Exception {
    testForR8(parameters.getBackend())
        .setMode(CompilationMode.DEBUG)
        .addProgramFiles(
            compiledJars.getForConfiguration(kotlinc, kotlinParameters.getTargetVersion()),
            kotlinc.getKotlinAnnotationJar())
        .addProgramFiles(getJavaJarFile(FOLDER))
        .setMinApi(parameters)
        .addKeepMainRule(MAIN_CLASS)
        .allowDiagnosticWarningMessages()
        .compile()
        .assertAllWarningMessagesMatch(equalTo("Resource 'META-INF/MANIFEST.MF' already exists."));
  }
}
