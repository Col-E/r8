// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.resolution.virtualtargets;

import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.resolution.virtualtargets.package_a.Middle;
import com.android.tools.r8.resolution.virtualtargets.package_a.Top;
import com.android.tools.r8.resolution.virtualtargets.package_a.TopRunner;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PackagePrivateChainTest extends TestBase {

  private static final String[] EXPECTED = new String[] {"Middle.clear()", "Bottom.clear()"};

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public PackagePrivateChainTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testRuntime() throws ExecutionException, CompilationFailedException, IOException {
    TestRunResult<?> runResult =
        testForRuntime(parameters.getRuntime(), parameters.getApiLevel())
            .addProgramClasses(Top.class, Middle.class, Bottom.class, TopRunner.class, Main.class)
            .run(parameters.getRuntime(), Main.class);
    if (parameters.isDexRuntime()
        && parameters.getRuntime().asDex().getVm().isOlderThanOrEqual(DexVm.ART_4_4_4_TARGET)) {
      runResult.assertFailureWithErrorThatMatches(containsString("clear overrides final"));
    } else {
      runResult.assertSuccessWithOutputLines(EXPECTED);
    }
  }

  @Test
  public void testR8() throws ExecutionException, CompilationFailedException, IOException {
    // TODO(b/148584615): Fix test.
    testForR8(parameters.getBackend())
        .addProgramClasses(Top.class, Middle.class, Bottom.class, TopRunner.class, Main.class)
        .addKeepMainRule(Main.class)
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Bottom.clear()", "Bottom.clear()");
  }

  public static class Bottom extends Middle {

    public void clear() {
      System.out.println("Bottom.clear()");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      Bottom bottom = new Bottom();
      TopRunner.run(bottom);
      bottom.clear();
    }
  }
}
