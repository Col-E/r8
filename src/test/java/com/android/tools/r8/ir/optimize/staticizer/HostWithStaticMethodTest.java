// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.staticizer;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ir.optimize.staticizer.HostWithStaticMethodTest.Outer.SingletonHolder;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
// This is a reproduction of b/158018192.
public class HostWithStaticMethodTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public HostWithStaticMethodTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws ExecutionException, CompilationFailedException, IOException {
    testForR8(parameters.getBackend())
        .addProgramClasses(Outer.class, SingletonHolder.class, Main.class)
        .addKeepMainRule(Main.class)
        .setMinApi(parameters)
        .enableNeverClassInliningAnnotations()
        .enableInliningAnnotations()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("foo", "bar", "foo");
  }

  @NeverClassInline
  public static class Outer {

    public static class SingletonHolder {

      public static final Outer outer = new Outer();

      @NeverInline
      // This method should not be in conflict with any methods in Outer.
      public static void foo2() {
        foo();
      }
    }

    @NeverInline
    public static void foo() {
      System.out.println("foo");
    }

    @NeverInline
    public void bar() {
      System.out.println("bar");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      Outer.foo();
      SingletonHolder.outer.bar();
      SingletonHolder.foo2();
    }
  }
}
