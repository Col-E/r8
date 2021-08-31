// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.api;

import static com.android.tools.r8.retrace.api.RetraceApiTestHelper.runJunitOnTests;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersBuilder;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import java.nio.file.Files;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runners.Parameterized.Parameters;

public abstract class RetraceApiTestBase extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return TestParametersBuilder.builder().withSystemRuntime().build();
  }

  public RetraceApiTestBase(TestParameters parameters) {
    this.parameters = parameters;
  }

  protected abstract Class<? extends RetraceApiBinaryTest> binaryTestClass();

  @Test
  public void testDirect() {
    Result result = JUnitCore.runClasses(binaryTestClass());
    for (Failure failure : result.getFailures()) {
      System.out.println(failure.toString());
    }
    assertTrue(result.wasSuccessful());
  }

  @Test
  public void testRetraceLib() throws Exception {
    Assume.assumeTrue(Files.exists(ToolHelper.R8_RETRACE_JAR));
    runJunitOnTests(
        parameters.getRuntime().asCf(), ToolHelper.R8_RETRACE_JAR, binaryTestClass(), temp);
  }
}
