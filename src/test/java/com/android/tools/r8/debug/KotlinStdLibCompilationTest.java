// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import static com.android.tools.r8.ToolHelper.getKotlinCompilers;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.KotlinCompilerTool.KotlinCompiler;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersBuilder;
import com.android.tools.r8.ToolHelper;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KotlinStdLibCompilationTest extends TestBase {

  private final TestParameters parameters;
  private final KotlinCompiler kotlinc;

  @Parameters(name = "{0}, kotlinc: {1}")
  public static List<Object[]> setup() {
    return buildParameters(
        TestParametersBuilder.builder().withAllRuntimesAndApiLevels().build(),
        getKotlinCompilers());
  }

  public KotlinStdLibCompilationTest(TestParameters parameters, KotlinCompiler kotlinc) {
    this.parameters = parameters;
    this.kotlinc = kotlinc;
  }

  @Test
  public void testD8() throws CompilationFailedException {
    assumeTrue(parameters.isDexRuntime());
    testForD8()
        .addProgramFiles(ToolHelper.getKotlinStdlibJar(kotlinc))
        .setMinApi(parameters.getApiLevel())
        .compileWithExpectedDiagnostics(TestDiagnosticMessages::assertNoMessages);
  }

  @Test
  public void testR8() throws CompilationFailedException {
    testForR8(parameters.getBackend())
        .addProgramFiles(ToolHelper.getKotlinStdlibJar(kotlinc))
        .noMinification()
        .noTreeShaking()
        .addKeepAllAttributes()
        .addDontWarnJetBrainsAnnotations()
        .setMode(CompilationMode.DEBUG)
        .setMinApi(parameters.getApiLevel())
        .compileWithExpectedDiagnostics(TestDiagnosticMessages::assertNoMessages);
  }
}
