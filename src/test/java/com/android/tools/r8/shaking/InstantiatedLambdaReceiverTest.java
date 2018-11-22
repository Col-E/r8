// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InstantiatedLambdaReceiverTest extends TestBase {

  private Backend backend;

  private static final String expectedOutput = "In C.m()";

  @Parameters(name = "Backend: {0}")
  public static Backend[] data() {
    return Backend.values();
  }

  public InstantiatedLambdaReceiverTest(Backend backend) {
    this.backend = backend;
  }

  @Test
  public void jvmTest() throws Exception {
    assumeTrue(
        "JVM test independent of Art version - only run when testing on latest",
        ToolHelper.getDexVm().getVersion().isLatest());
    testForJvm().addTestClasspath().run(TestClass.class).assertSuccessWithOutput(expectedOutput);
  }

  @Test
  public void dexTest() throws Exception {
    testForR8(backend)
        .addInnerClasses(InstantiatedLambdaReceiverTest.class)
        .addKeepMainRule(TestClass.class)
        .run(TestClass.class)
        .assertSuccessWithOutput(expectedOutput);
  }

  interface I {
    void m();
  }

  interface II extends I {}

  static class C implements I {

    @Override
    public void m() {
      System.out.print("In C.m()");
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      I i = new C();
      II x = i::m; // This should mark II as being instantiated!
      x.m();
    }
  }
}
