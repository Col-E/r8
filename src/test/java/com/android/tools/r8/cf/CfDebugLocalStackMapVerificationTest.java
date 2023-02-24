// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf;

import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** Regression tests for b/237567012 */
@RunWith(Parameterized.class)
public class CfDebugLocalStackMapVerificationTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withCfRuntimes().build();
  }

  static class SmallRepro {

    public static void main(String[] args) {
      RuntimeException x = new RuntimeException("FOO");
      RuntimeException c = null;
      try {
        c = x;
        throw c;
      } catch (RuntimeException e) {
        System.out.println(c);
      }
    }
  }

  @Test
  public void testReference() throws Exception {
    testForJvm(parameters)
        .addProgramClasses(SmallRepro.class)
        .run(parameters.getRuntime(), SmallRepro.class)
        .assertSuccessWithOutputThatMatches(containsString("FOO"));
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters.getBackend())
        .addProgramClasses(SmallRepro.class)
        .setMinApi(AndroidApiLevel.B)
        .addOptionsModification(
            options -> {
              options.testing.forceIRForCfToCfDesugar = true;
              options.testing.neverReuseCfLocalRegisters = true;
            })
        .run(parameters.getRuntime(), SmallRepro.class)
        .assertSuccessWithOutputThatMatches(containsString("FOO"));
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .debug()
        .addProgramClasses(SmallRepro.class)
        .addKeepAllAttributes()
        .addDontShrink()
        .addDontObfuscate()
        .addDontOptimize()
        .addOptionsModification(
            options -> {
              options.testing.neverReuseCfLocalRegisters = true;
              // TODO(b/237567012): Remove option when resolved.
              options.enableCheckAllInstructionsDuringStackMapVerification = true;
            })
        .run(parameters.getRuntime(), SmallRepro.class)
        .assertSuccessWithOutputThatMatches(containsString("FOO"));
  }
}
