// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.nest;

import static com.android.tools.r8.TestRuntime.CfVm.JDK11;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.desugar.nest.NestBasedAccessToNativeMethodTest.Main.Inner;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class NestBasedAccessToNativeMethodTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimesStartingFromIncluding(JDK11)
        .withDexRuntimes()
        .withAllApiLevels()
        .build();
  }

  public NestBasedAccessToNativeMethodTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8()
        .addProgramClassFileData(getProgramClassFileData())
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello from native");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClassFileData(getProgramClassFileData())
        .addKeepMainRule(Main.class)
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello from native");
  }

  private List<byte[]> getProgramClassFileData() throws IOException, NoSuchMethodException {
    return ImmutableList.of(
        transformer(Main.class).setNest(Main.class, Inner.class).transform(),
        transformer(Inner.class)
            .setPrivate(Inner.class.getDeclaredMethod("goingToBePrivate"))
            .setNest(Main.class, Inner.class)
            .transform());
  }

  static class Main {

    public static void main(String[] args) {
      try {
        Inner.goingToBePrivate();
      } catch (UnsatisfiedLinkError e) {
        System.out.println("Hello from native");
      }
    }

    static class Inner {

      /*private*/ static native void goingToBePrivate();
    }
  }
}
