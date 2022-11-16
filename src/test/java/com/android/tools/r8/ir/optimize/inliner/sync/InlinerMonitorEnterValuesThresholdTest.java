// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner.sync;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InlinerMonitorEnterValuesThresholdTest extends TestBase {

  private final TestParameters parameters;
  private final int threshold;

  @Parameters(name = "{0},  threshold: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), ImmutableList.of(2, 3));
  }

  public InlinerMonitorEnterValuesThresholdTest(TestParameters parameters, int threshold) {
    this.parameters = parameters;
    this.threshold = threshold;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class)
        .addKeepMainRule(TestClass.class)
        .addOptionsModification(
            options -> options.inlinerOptions().inliningMonitorEnterValuesAllowance = threshold)
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(TestClass.class);
    assertThat(classSubject, isPresent());
    assertThat(classSubject.mainMethod(), isPresent());
    assertThat(classSubject.uniqueMethodWithOriginalName("m1"), not(isPresent()));
    assertThat(classSubject.uniqueMethodWithOriginalName("m2"), not(isPresent()));
    if (threshold == 2) {
      assertThat(classSubject.uniqueMethodWithOriginalName("m3"), isPresent());
    } else {
      assert threshold == 3;
      assertThat(classSubject.uniqueMethodWithOriginalName("m3"), not(isPresent()));
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      synchronized (TestClass.class) {
        // Method m1() contributes with 1 extra monitor-enter value and should therefore always be
        // inlined since the threshold is 2 or 3.
        m1();

        // Method m2() contributes with no extra monitor-enter values since it uses TestClass.class
        // as a lock, which is already used as a lock in the enclosing class. Therefore, m2() should
        // always be inlined.
        m2();

        // Method m3() contributes with 1 extra monitor-enter value and should therefore only be
        // inlined if the threshold is 3.
        m3();
      }
    }

    private static void m1() {
      synchronized (new Object()) {
        System.out.print("Hello");
      }
    }

    private static synchronized void m2() {
      System.out.print(" world");
    }

    private static void m3() {
      synchronized (new Object()) {
        System.out.println("!");
      }
    }
  }
}
