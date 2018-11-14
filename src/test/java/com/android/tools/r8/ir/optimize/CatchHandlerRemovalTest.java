// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CatchHandlerRemovalTest extends TestBase {

  private final Backend backend;

  @Parameters(name = "Backend: {0}")
  public static Backend[] data() {
    return Backend.values();
  }

  public CatchHandlerRemovalTest(Backend backend) {
    this.backend = backend;
  }

  @Test
  public void test() throws Exception {
    String expected = "In TestClass.method()";

    testForJvm().addTestClasspath().run(TestClass.class).assertSuccessWithOutput(expected);

    testForR8(backend)
        .addInnerClasses(CatchHandlerRemovalTest.class)
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .run(TestClass.class)
        .assertSuccessWithOutput(expected)
        .inspector();
  }

  static class TestClass {

    public static void main(String[] args) {
      // Instantiate ExceptionA and ExceptionB such that the uninstantiated type optimization
      // cannot unlink the catch handlers guarded by these types.
      new ExceptionA();
      new ExceptionB();

      // Since ExceptionC is never instantiated, the catch handler for it will be removed by the
      // uninstantiated type optimization.
      try {
        method();
      } catch (ExceptionA | ExceptionB | ExceptionC e) {
        System.out.println("Caught exception");
      }
    }

    @NeverInline
    public static void method() {
      System.out.print("In TestClass.method()");
    }
  }

  static class ExceptionA extends RuntimeException {}

  static class ExceptionB extends RuntimeException {}

  static class ExceptionC extends RuntimeException {}
}
