// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.debug;

import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ContinuousKotlinSteppingTest extends DebugTestBase {

  private static final String MAIN_METHOD_NAME = "main";

  @Parameters(name = "{0}, {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(),
        getKotlinTestParameters().withAllCompilersAndTargetVersions().build());
  }

  private final TestParameters parameters;
  private final KotlinTestParameters kotlinParameters;

  public ContinuousKotlinSteppingTest(
      TestParameters parameters, KotlinTestParameters kotlinParameters) {
    this.parameters = parameters;
    this.kotlinParameters = kotlinParameters;
  }

  @Test
  public void testContinuousSingleStepKotlinApp() throws Throwable {
    KotlinDebugD8Config d8Config =
        KotlinDebugD8Config.build(
            kotlinParameters, parameters.getApiLevel(), parameters.getRuntime().asDex());
    runContinuousTest("KotlinApp", d8Config, MAIN_METHOD_NAME);
  }

  @Test
  public void testContinuousSingleStepKotlinInline() throws Throwable {
    KotlinDebugD8Config d8Config =
        KotlinDebugD8Config.build(
            kotlinParameters, parameters.getApiLevel(), parameters.getRuntime().asDex());
    runContinuousTest("KotlinInline", d8Config, MAIN_METHOD_NAME);
  }
}
