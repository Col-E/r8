// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.lambda;

import static com.android.tools.r8.ToolHelper.getKotlinCompilers;
import static org.hamcrest.CoreMatchers.equalTo;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.KotlinCompilerTool.KotlinCompiler;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import com.android.tools.r8.kotlin.AbstractR8KotlinTestBase;
import java.util.List;
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
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(), getKotlinCompilers());
  }

  public KotlinLambdaMergingDebugTest(TestParameters parameters, KotlinCompiler kotlinc) {
    super(KotlinTargetVersion.JAVA_6, kotlinc);
    this.parameters = parameters;
  }

  private static final KotlinCompileMemoizer compiledJars =
      getCompileMemoizer(getKotlinFilesInResource(FOLDER), FOLDER)
          .configure(kotlinCompilerTool -> kotlinCompilerTool.includeRuntime().noReflect());

  @Test
  public void testMergingKStyleLambdasAndReprocessingInDebug() throws Exception {
    testForR8(parameters.getBackend())
        .setMode(CompilationMode.DEBUG)
        .addProgramFiles(compiledJars.getForConfiguration(kotlinc, KotlinTargetVersion.JAVA_6))
        .addProgramFiles(getJavaJarFile(FOLDER))
        .addDontWarnJetBrainsAnnotations()
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(MAIN_CLASS)
        .allowDiagnosticWarningMessages()
        .compile()
        .assertAllWarningMessagesMatch(equalTo("Resource 'META-INF/MANIFEST.MF' already exists."));
  }
}
