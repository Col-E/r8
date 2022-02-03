// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.rewrite.assertions;

import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.rewrite.assertions.assertionhandler.AssertionHandlers;
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
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build());
  }

  abstract String getExpectedOutput();

  abstract MethodReference getAssertionHandler() throws Exception;

  abstract List<Class<?>> getTestClasses();

  private MethodReference getAssertionHandlerIgnoreException() {
    try {
      return getAssertionHandler();
    } catch (Exception e) {
      return null;
    }
  }

  @Test
  public void testD8() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForD8(parameters.getBackend())
        .addProgramClasses(AssertionHandlers.class)
        .addProgramClasses(getTestClasses())
        .setMinApi(parameters.getApiLevel())
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
    testForR8(parameters.getBackend())
        .addProgramClasses(AssertionHandlers.class)
        .addProgramClasses(getTestClasses())
        .addKeepMainRule(getTestClasses().get(0))
        .addKeepAnnotation()
        .addKeepRules("-keep class * { @com.android.tools.r8.Keep *; }")
        .setMinApi(parameters.getApiLevel())
        .addAssertionsConfiguration(
            builder ->
                builder
                    .setAssertionHandler(getAssertionHandlerIgnoreException())
                    .setScopeAll()
                    .build())
        .run(parameters.getRuntime(), getTestClasses().get(0))
        .assertSuccessWithOutput(getExpectedOutput());
  }
}
