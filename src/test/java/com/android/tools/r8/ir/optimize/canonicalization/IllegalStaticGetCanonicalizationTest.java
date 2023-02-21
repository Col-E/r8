// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.canonicalization;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.ReprocessClassInitializer;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class IllegalStaticGetCanonicalizationTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection params() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public IllegalStaticGetCanonicalizationTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(IllegalStaticGetCanonicalizationTest.class)
        .addKeepMainRule(TestClass.class)
        .addOptionsModification(options -> options.enableRedundantFieldLoadElimination = false)
        .enableInliningAnnotations()
        .enableReprocessClassInitializerAnnotations()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello!", "Hello!");
  }

  @ReprocessClassInitializer
  static class TestClass {

    static TestClass INSTANCE;

    static {
      INSTANCE = new TestClass();
      INSTANCE.method();
      INSTANCE.method();
    }

    public static void main(String[] args) {}

    @NeverInline
    void method() {
      System.out.println("Hello!");
    }
  }
}
