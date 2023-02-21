// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.resolution;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.TestRuntime.CfVm;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SingleResolutionWithFailingDispatchTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection parameters() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(Main.class, I.class, J.class)
        .addProgramClassFileData(getProgramClassFileData())
        .run(parameters.getRuntime(), Main.class)
        .apply(this::inspectRunResult);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class, I.class, J.class)
        .addProgramClassFileData(getProgramClassFileData())
        .addKeepMainRule(Main.class)
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .apply(this::inspectRunResult);
  }

  private void inspectRunResult(TestRunResult<?> runResult) {
    if (parameters.isCfRuntime() && parameters.asCfRuntime().isNewerThanOrEqual(CfVm.JDK11)) {
      runResult.assertFailureWithErrorThatThrows(AbstractMethodError.class);
    } else {
      runResult.assertFailureWithErrorThatThrows(IncompatibleClassChangeError.class);
    }
  }

  private static byte[] getProgramClassFileData() throws IOException {
    return transformer(A.class).setImplements(I.class, J.class).transform();
  }

  static class Main {

    public static void main(String[] args) {
      I i = new A();
      i.m();
    }
  }

  interface I {

    default void m() {
      System.out.println("I.m()");
    }
  }

  interface J {

    default void m() {
      System.out.println("J.m()");
    }
  }

  static class A implements I /*, J*/ {}
}
