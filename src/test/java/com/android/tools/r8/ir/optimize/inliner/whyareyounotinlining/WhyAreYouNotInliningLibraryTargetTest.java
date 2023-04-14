// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner.whyareyounotinlining;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class WhyAreYouNotInliningLibraryTargetTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultRuntimes().withApiLevel(AndroidApiLevel.B).build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .addKeepRules(
            "-whyareyounotinlining class "
                + System.class.getTypeName()
                + " { long currentTimeMillis(); }")
        .enableExperimentalWhyAreYouNotInlining()
        .setMinApi(parameters)
        .compile()
        // The Inliner is currently not reporting -whyareyounotinlining for library calls.
        .assertNoInfoMessages();
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(System.currentTimeMillis() > 0 ? "A" : "B");
    }
  }
}
