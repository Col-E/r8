// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.devirtualize;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DevirtualizeWithCatchHandlersTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return TestBase.getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public DevirtualizeWithCatchHandlersTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepRules(
            // Disable uninstantiated type optimization for m().
            "-keepclassmembers class " + Uninstantiated.class.getTypeName() + " {",
            "  " + Uninstantiated.class.getTypeName() + " get();",
            "}")
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A");
  }

  static class Main {

    public static void main(String[] args) {
      try {
        test();
      } catch (Exception e) {
        System.out.println("Dead!");
      }
    }

    static synchronized void test() {
      I which = System.currentTimeMillis() > 0 ? new A() : Uninstantiated.get();
      which.m();
    }
  }

  interface I {

    void m();
  }

  static class A implements I {

    @NeverInline
    @Override
    public void m() {
      System.out.println("A");
    }
  }

  static class Uninstantiated implements I {

    static Uninstantiated get() {
      return null;
    }

    @Override
    public void m() {
      throw new RuntimeException();
    }
  }
}
