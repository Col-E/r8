// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.rewrite.assertionerror;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.rewrite.assertionerror.AssertionErrorRewriteTest.Main;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

// This is a reproduction of b/139451198 that only shows when compiling with min api-level 16, 17 or
// 18. There is no compatible checked in DexRuntime so asking for AndroidLevel.J in testparameters
// will service 15 or 19. We therefore fix the min api-level in the test to J.
@RunWith(Parameterized.class)
public class AssertionErrorRewriteApi16Test extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimesStartingFromIncluding(Version.V4_4_4).build();
  }

  public AssertionErrorRewriteApi16Test(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void r8TestSplitBlock_b139451198()
      throws ExecutionException, CompilationFailedException, IOException {
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .setMinApi(AndroidApiLevel.J)
        .run(parameters.getRuntime(), Main.class, String.valueOf(false))
        .assertSuccessWithOutputLines("message", "java.lang.RuntimeException: cause message");
  }
}
