// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.interfacebridge;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class LambdaAbstractMethodErrorTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().build();
  }

  public LambdaAbstractMethodErrorTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test_b133457361() throws ExecutionException, CompilationFailedException, IOException {
    testForR8(parameters.getBackend())
        .addProgramClassesAndInnerClasses(Main.class)
        .addProgramClassesAndInnerClasses(Task.class, OuterClass.class)
        .addKeepMainRule(Main.class)
        .addOptionsModification(
            internalOptions -> {
              internalOptions.enableInlining = false;
              internalOptions.enableClassInlining = false;
              internalOptions.enableVerticalClassMerging = false;
            })
        .setMinApi(parameters.getRuntime())
        .run(Main.class.getTypeName())
        .assertSuccessWithOutput("FOO");
  }
}
