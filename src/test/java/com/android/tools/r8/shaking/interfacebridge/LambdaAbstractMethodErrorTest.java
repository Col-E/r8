// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.interfacebridge;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class LambdaAbstractMethodErrorTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimesAndAllApiLevels().build();
  }

  @Test
  public void test_b133457361() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClassesAndInnerClasses(Main.class)
        .addProgramClassesAndInnerClasses(Task.class, OuterClass.class)
        .addKeepMainRule(Main.class)
        .addOptionsModification(
            options -> {
              options.inlinerOptions().enableInlining = false;
              options.enableClassInlining = false;
              options.getVerticalClassMergerOptions().disable();
            })
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class.getTypeName())
        .assertSuccessWithOutput("FOO");
  }
}
