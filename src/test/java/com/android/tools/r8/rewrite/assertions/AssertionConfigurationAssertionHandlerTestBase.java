// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.rewrite.assertions;


import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.rewrite.assertions.assertionhandler.AssertionHandlers;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public abstract class AssertionConfigurationAssertionHandlerTestBase extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  abstract String getExpectedOutput();

  abstract MethodReference getAssertionHandler() throws Exception;

  abstract List<Class<?>> getTestClasses();

  protected List<Class<?>> getAssertionHandlerClasses() {
    return ImmutableList.of(AssertionHandlers.class);
  }

  protected void configure(R8TestBuilder<?> builder) {}

  protected void inspect(CodeInspector inspector) {}

  private MethodReference getAssertionHandlerIgnoreException() {
    try {
      return getAssertionHandler();
    } catch (Exception e) {
      return null;
    }
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8()
        .addProgramClasses(getAssertionHandlerClasses())
        .addProgramClasses(getTestClasses())
        .setMinApi(parameters)
        .addOptionsModification(o -> o.testing.forceIRForCfToCfDesugar = true)
        .addAssertionsConfiguration(
            builder ->
                builder
                    .setAssertionHandler(getAssertionHandlerIgnoreException())
                    .setScopeAll()
                    .build())
        .run(parameters.getRuntime(), getTestClasses().get(0))
        .assertSuccessWithOutput(getExpectedOutput());
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .addProgramClasses(getAssertionHandlerClasses())
        .addProgramClasses(getTestClasses())
        .addKeepMainRule(getTestClasses().get(0))
        .addKeepAnnotation()
        .addKeepRules("-keepclassmembers class * { @com.android.tools.r8.Keep *; }")
        .setMinApi(parameters)
        .addAssertionsConfiguration(
            builder ->
                builder
                    .setAssertionHandler(getAssertionHandlerIgnoreException())
                    .setScopeAll()
                    .build())
        .apply(this::configure)
        .run(parameters.getRuntime(), getTestClasses().get(0))
        .inspect(this::inspect)
        .assertSuccessWithOutput(getExpectedOutput());
  }
}
