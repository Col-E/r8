// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.workaround;

import static org.junit.Assume.assumeFalse;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.utils.BooleanUtils;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class IncorrectTypeRefinementForThrowableTest extends TestBase {

  @Parameter(0)
  public boolean enableCheckCastAndInstanceOfRemoval;

  @Parameter(1)
  public TestParameters parameters;

  @Parameters(name = "{1}, optimize: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addOptionsModification(
            options ->
                options.testing.enableCheckCastAndInstanceOfRemoval =
                    enableCheckCastAndInstanceOfRemoval)
        .release()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        // TODO(b/288273207): Should remove the instanceof check.
        .applyIf(
            parameters.getDexRuntimeVersion().isDalvik(),
            TestRunResult::assertSuccessWithEmptyOutput,
            runResult -> runResult.assertFailureWithErrorThatThrows(VerifyError.class));
  }

  @Test
  public void testJvm() throws Exception {
    assumeFalse(enableCheckCastAndInstanceOfRemoval);
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithEmptyOutput();
  }

  static class Main {

    public static void main(String[] args) {
      RuntimeException o = null;
      if (o instanceof Object) {
        throw o;
      }
    }
  }
}
