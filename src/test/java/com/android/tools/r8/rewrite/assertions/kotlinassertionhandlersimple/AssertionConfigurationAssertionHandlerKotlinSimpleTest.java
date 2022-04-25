// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.rewrite.assertions.kotlinassertionhandlersimple;

import static org.hamcrest.CoreMatchers.equalTo;

import com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.rewrite.assertions.AssertionConfigurationAssertionHandlerKotlinTestBase;
import com.android.tools.r8.rewrite.assertions.assertionhandler.AssertionHandlers;
import com.android.tools.r8.utils.StringUtils;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class AssertionConfigurationAssertionHandlerKotlinSimpleTest
    extends AssertionConfigurationAssertionHandlerKotlinTestBase {

  public AssertionConfigurationAssertionHandlerKotlinSimpleTest(
      TestParameters parameters,
      KotlinTestParameters kotlinParameters,
      boolean kotlinStdlibAsClasspath,
      boolean useJvmAssertions)
      throws IOException {
    super(parameters, kotlinParameters, kotlinStdlibAsClasspath, useJvmAssertions);
  }

  @Override
  protected String getExpectedOutput() {
    return StringUtils.lines(
        "assertionHandler: simpleAssertion",
        "assertionHandler: multipleAssertions 1",
        "assertionHandler: multipleAssertions 2");
  }

  @Override
  protected MethodReference getAssertionHandler() throws Exception {
    return Reference.methodFromMethod(
        AssertionHandlers.class.getMethod("assertionHandler", Throwable.class));
  }

  @Override
  protected List<Path> getKotlinFiles() throws IOException {
    return getKotlinFilesInTestPackage(getClass().getPackage());
  }

  @Override
  protected String getTestClassName() {
    return getClass().getPackage().getName() + ".AssertionSimpleKt";
  }

  @Override
  protected void configureR8(R8FullTestBuilder builder) {
    boolean referencesNotNull =
        !kotlinParameters.is(KotlinCompilerVersion.KOTLINC_1_3_72)
            && !kotlinStdlibAsLibrary
            && !useJvmAssertions;
    builder
        .applyIf(referencesNotNull, b -> b.addDontWarn("org.jetbrains.annotations.NotNull"))
        .allowDiagnosticWarningMessages(!kotlinStdlibAsLibrary);
  }

  @Override
  protected void configureResultR8(R8TestCompileResult builder) {
    builder.applyIf(
        !kotlinStdlibAsLibrary,
        result ->
            result.assertAllWarningMessagesMatch(
                equalTo("Resource 'META-INF/MANIFEST.MF' already exists.")));
  }
}
