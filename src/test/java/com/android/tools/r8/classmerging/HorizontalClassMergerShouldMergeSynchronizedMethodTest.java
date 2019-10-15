// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class HorizontalClassMergerShouldMergeSynchronizedMethodTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public HorizontalClassMergerShouldMergeSynchronizedMethodTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testNoMergingOfClassUsedInMonitor()
      throws IOException, CompilationFailedException, ExecutionException {
    testForR8(parameters.getBackend())
        .addInnerClasses(HorizontalClassMergerShouldMergeSynchronizedMethodTest.class)
        .addKeepMainRule(Main.class)
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("1", "2", "3")
        .inspect(
            inspector -> {
              assertFalse(
                  inspector.clazz(LockOne.class).isPresent()
                      && inspector.clazz(LockTwo.class).isPresent());
              assertTrue(
                  inspector.clazz(LockOne.class).isPresent()
                      || inspector.clazz(LockTwo.class).isPresent());
            });
  }

  // Will be merged with LockTwo.
  static class LockOne {

    static synchronized void print1() {
      System.out.println("1");
    }

    static synchronized void print2() {
      System.out.println("2");
    }
  }

  public static class LockTwo {

    static void print3() {
      System.out.println("3");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      LockOne.print1();
      LockOne.print2();
      LockTwo.print3();
    }
  }
}
