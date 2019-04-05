// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debuginfo;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class Regress129901036TestRunner extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntime(Version.V5_1_1).build();
  }

  private final TestParameters parameters;

  public Regress129901036TestRunner(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForD8()
        .addProgramClasses(Regress129901036Test.class)
        .setMode(CompilationMode.RELEASE)
        .setMinApi(parameters.getRuntime().asDex().getMinApiLevel())
        .run(parameters.getRuntime(), Regress129901036Test.class)
        .assertSuccessWithOutput("aok");
  }
}
