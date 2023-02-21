// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner;

import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ClassInlinerNullableStaticGetTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatThrows(NullPointerException.class);
  }

  static class Main {

    public static void main(String[] args) {
      Singleton.get().greet();
      Singleton.set();
      if (System.currentTimeMillis() < 0) {
        System.out.println(new Singleton());
      }
    }
  }

  @NoVerticalClassMerging
  static class Singleton {

    static Singleton INSTANCE;

    static Singleton get() {
      return INSTANCE;
    }

    static void set() {
      INSTANCE = new SingletonImpl();
    }

    void greet() {
      System.out.println("Hello!");
    }
  }

  static class SingletonImpl extends Singleton {}
}
