// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.DiagnosticsMatcher;
import com.android.tools.r8.KotlinCompilerTool.KotlinCompiler;
import com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.errors.InterfaceDesugarMissingTypeDiagnostic;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KotlinStdLibCompilationTest extends TestBase {

  private final TestParameters parameters;
  private final KotlinTestParameters kotlinTestParameters;

  @Parameters(name = "{0}, kotlinc: {1}")
  public static List<Object[]> setup() {
    return buildParameters(
        TestParameters.builder().withAllRuntimesAndApiLevels().build(),
        getKotlinTestParameters().withAllCompilers().withNoTargetVersion().build());
  }

  public KotlinStdLibCompilationTest(
      TestParameters parameters, KotlinTestParameters kotlinTestParameters) {
    this.parameters = parameters;
    this.kotlinTestParameters = kotlinTestParameters;
  }

  @Test
  public void testD8() throws CompilationFailedException {
    assumeTrue(parameters.isDexRuntime());
    testForD8()
        .addProgramFiles(kotlinTestParameters.getCompiler().getKotlinStdlibJar())
        .setMinApi(parameters.getApiLevel())
        .compileWithExpectedDiagnostics(
            diagnostics -> {
              if (kotlinTestParameters.isNewerThanOrEqualTo(KotlinCompilerVersion.KOTLINC_1_8_0)
                  && parameters.isDexRuntime()
                  && parameters.getApiLevel().isLessThan(AndroidApiLevel.N)) {
                diagnostics.assertWarningsCount(2);
                diagnostics.assertAllWarningsMatch(
                    DiagnosticsMatcher.diagnosticType(InterfaceDesugarMissingTypeDiagnostic.class));
              } else {
                diagnostics.assertNoMessages();
              }
            });
  }

  @Test
  public void testR8() throws CompilationFailedException {
    KotlinCompiler compiler = kotlinTestParameters.getCompiler();
    testForR8(parameters.getBackend())
        .addProgramFiles(compiler.getKotlinStdlibJar(), compiler.getKotlinAnnotationJar())
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.LATEST))
        .addKeepAllAttributes()
        .allowDiagnosticWarningMessages()
        .addDontObfuscate()
        .noTreeShaking()
        .setMode(CompilationMode.DEBUG)
        .setMinApi(parameters.getApiLevel())
        .applyIf(
            parameters.isCfRuntime()
                && kotlinTestParameters.isNewerThanOrEqualTo(KotlinCompilerVersion.KOTLINC_1_8_0),
            TestShrinkerBuilder::addDontWarnJavaLangInvokeLambdaMetadataFactory)
        .compile()
        .assertAllWarningMessagesMatch(equalTo("Resource 'META-INF/MANIFEST.MF' already exists."));
  }
}
