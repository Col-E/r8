// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.lambda;

import static com.android.tools.r8.ToolHelper.getJava8RuntimeJar;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.KotlinTestBase;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.DescriptorUtils;
import java.nio.file.Path;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class KotlinLambdaMergerValidationTest extends KotlinTestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}, {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        getKotlinTestParameters().withAllCompilersAndTargetVersions().build());
  }

  public KotlinLambdaMergerValidationTest(
      TestParameters parameters, KotlinTestParameters kotlinParameters) {
    super(kotlinParameters);
    this.parameters = parameters;
  }

  @Test
  public void testR8_excludeKotlinStdlib() throws Exception {
    assumeTrue(parameters.isCfRuntime());

    String pkg = getClass().getPackage().getName();
    String folder = DescriptorUtils.getBinaryNameFromJavaType(pkg);
    Path ktClasses =
        kotlinc(getKotlincHostRuntime(parameters.getRuntime()), kotlinc, targetVersion)
            .addSourceFiles(getKotlinFileInTest(folder, "b143165163"))
            .compile();
    testForR8(parameters.getBackend())
        .addLibraryFiles(getJava8RuntimeJar())
        .addLibraryFiles(kotlinc.getKotlinStdlibJar())
        .addProgramFiles(ktClasses, kotlinc.getKotlinAnnotationJar())
        .addKeepMainRule("**.B143165163Kt")
        .allowDiagnosticWarningMessages()
        .setMinApi(parameters)
        .compile()
        .assertAllWarningMessagesMatch(equalTo("Resource 'META-INF/MANIFEST.MF' already exists."))
        .addRunClasspathFiles(kotlinc.getKotlinStdlibJar())
        .run(parameters.getRuntime(), pkg + ".B143165163Kt")
        .assertSuccessWithOutputLines("outer foo bar", "outer foo default");
  }

  @Test
  public void testR8_includeKotlinStdlib() throws Exception {
    assumeTrue(parameters.isDexRuntime());

    String pkg = getClass().getPackage().getName();
    String folder = DescriptorUtils.getBinaryNameFromJavaType(pkg);
    Path ktClasses =
        kotlinc(getKotlincHostRuntime(parameters.getRuntime()), kotlinc, targetVersion)
            .addSourceFiles(getKotlinFileInTest(folder, "b143165163"))
            .compile();
    testForR8(parameters.getBackend())
        .addProgramFiles(kotlinc.getKotlinStdlibJar(), kotlinc.getKotlinAnnotationJar())
        .addProgramFiles(ktClasses)
        .addKeepMainRule("**.B143165163Kt")
        .allowDiagnosticWarningMessages()
        .setMinApi(parameters)
        .compile()
        .assertAllWarningMessagesMatch(equalTo("Resource 'META-INF/MANIFEST.MF' already exists."))
        .run(parameters.getRuntime(), pkg + ".B143165163Kt")
        .assertSuccessWithOutputLines("outer foo bar", "outer foo default");
  }
}
