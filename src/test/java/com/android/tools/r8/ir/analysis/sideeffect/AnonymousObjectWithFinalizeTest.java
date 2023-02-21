// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.sideeffect;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

// See b/143189461
@RunWith(Parameterized.class)
public class AnonymousObjectWithFinalizeTest extends TestBase {

  final static String EXPECTED = StringUtils.lines(
      "set up fence",
      "run gc",
      "count down fence",
      "passed fence"
  );

  public static class TestClass {

    public static void main(String[] args) {
      Runtime.getRuntime().gc();
      Runtime.getRuntime().runFinalization();

      System.out.println("set up fence");
      final CountDownLatch fence = new CountDownLatch(1);
      new Object() {
        @Override
        protected void finalize() throws Throwable {
          try {
            System.out.println("count down fence");
            fence.countDown();
          } finally {
            super.finalize();
          }
        }
      };
      try {
        System.out.println("run gc");
        Runtime.getRuntime().gc();
        Runtime.getRuntime().runFinalization();
        if (fence.await(10, TimeUnit.SECONDS)) {
          System.out.println("passed fence");
        } else {
          System.out.println("fence await timed out");
        }
      } catch (InterruptedException ex) {
        throw new RuntimeException(ex);
      }
    }
  }

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  final TestParameters parameters;

  public AnonymousObjectWithFinalizeTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testReference() throws Exception {
    testForRuntime(parameters.getRuntime(), parameters.getApiLevel())
        .addProgramClassesAndInnerClasses(TestClass.class)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .setMinApi(parameters)
        .addKeepMainRule(TestClass.class)
        .addProgramClassesAndInnerClasses(TestClass.class)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }
}
