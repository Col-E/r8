// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AtomicFieldUpdaterTest extends TestBase {

  private static final String EXPECTED_OUTPUT = StringUtils.lines("Test succeeded");

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addTestClasspath()
        .run(parameters.getRuntime(), AtomicFieldUpdaterTestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(AtomicFieldUpdaterTestClass.class)
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              // Verify that the field is still there.
              ClassSubject classSubject =
                  inspector.clazz(AtomicFieldUpdaterTestClass.A.class.getName());
              assertThat(classSubject, isPresentAndRenamed());
              assertThat(classSubject.field("int", "field"), isPresentAndRenamed());
            })
        .run(parameters.getRuntime(), AtomicFieldUpdaterTestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  static class AtomicFieldUpdaterTestClass {

    public static void main(String[] args) {
      // Throws NoSuchFieldException if field is removed by tree shaking, or if the string "field"
      // is
      // not renamed as a result of minification.
      AtomicIntegerFieldUpdater.newUpdater(AtomicFieldUpdaterTestClass.A.class, "field");
      System.out.println("Test succeeded");
    }

    static class A {
      volatile int field;
    }
  }
}
