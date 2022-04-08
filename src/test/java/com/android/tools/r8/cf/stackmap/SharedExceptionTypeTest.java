// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf.stackmap;

import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SharedExceptionTypeTest extends TestBase {

  private final TestParameters parameters;
  private final String EXPECTED = "Hello World!";

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().build();
  }

  public SharedExceptionTypeTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testJvm() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    testForJvm()
        .addInnerClasses(SharedExceptionTypeTest.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testD8() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForD8(parameters.getBackend())
        .addInnerClasses(SharedExceptionTypeTest.class)
        .setMinApi(AndroidApiLevel.B)
        .addOptionsModification(options -> options.testing.readInputStackMaps = true)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  public static class ExceptionSuper extends RuntimeException {

    public void printError() {
      System.out.println("Hello World!");
    }
  }

  public static class ExceptionA extends ExceptionSuper {}

  public static class ExceptionB extends ExceptionSuper {}

  public static class Main {

    public static void foo(String[] args) {
      if (args.length == 0) {
        throw new ExceptionA();
      } else if (args.length == 1) {
        throw new ExceptionB();
      } else {
        throw new RuntimeException("FOO BAR");
      }
    }

    public static void main(String[] args) {
      try {
        foo(args);
      } catch (ExceptionA | ExceptionB e) {
        e.printError();
      }
    }
  }
}
