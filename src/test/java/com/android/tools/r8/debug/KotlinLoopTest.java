// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
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
public class KotlinLoopTest extends KotlinDebugTestBase {

  private final TestParameters parameters;
  private final KotlinTestParameters kotlinParameters;

  @Parameters(name = "{0}, {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(),
        getKotlinTestParameters().withAllCompilersAndTargetVersions().build());
  }

  public KotlinLoopTest(TestParameters parameters, KotlinTestParameters kotlinParameters) {
    this.parameters = parameters;
    this.kotlinParameters = kotlinParameters;
  }

  DebugTestConfig config() {
    return KotlinLoopD8Config.build(kotlinParameters, parameters.getApiLevel());
  }

  @Test
  public void testStepOver() throws Throwable {
    runDebugTest(
        config(),
        "loops.LoopKt",
        breakpoint("loops.LoopKt", "main"),
        run(),
        checkLine("Loop.kt", 13),
        run());
  }
}
