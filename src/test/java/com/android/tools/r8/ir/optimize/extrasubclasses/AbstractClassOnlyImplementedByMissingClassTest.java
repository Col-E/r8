// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.extrasubclasses;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AbstractClassOnlyImplementedByMissingClassTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public AbstractClassOnlyImplementedByMissingClassTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  /**
   * Tests that it is possible to provide an implementation of an abstract class after the program
   * has been compiled with R8, as long as the abstract class and its methods have been kept.
   */
  @Test
  public void test() throws Exception {
    Path r8Out =
        testForR8(parameters.getBackend())
            // B is not visible to the R8 compilation.
            .addProgramClasses(TestClass.class, A.class)
            // Helper is added on the classpath such that R8 doesn't know what it does.
            .addClasspathClasses(Helper.class)
            .addKeepMainRule(TestClass.class)
            // Keeping A, A.<init>(), and A.m() should make it possible to provide an implementation
            // of
            // A after the R8 compilation.
            .addKeepRules("-keep class " + A.class.getTypeName() + " { void <init>(); void m(); }")
            .setMinApi(parameters)
            .compile()
            .writeToZip();

    testForRuntime(parameters)
        .addProgramClasses(B.class, Helper.class)
        .addRunClasspathFiles(r8Out)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!", "The end");
  }

  static class TestClass {

    public static void main(String[] args) {
      Helper.getInstance().m();
      System.out.println("The end");
    }
  }

  abstract static class A {

    abstract void m();
  }

  // Only declarations are visible via the classpath.
  static class Helper {

    static A getInstance() {
      return new B();
    }
  }

  // Not visible during the R8 compilation.
  static class B extends A {

    @Override
    public void m() {
      System.out.println("Hello world!");
    }
  }
}
