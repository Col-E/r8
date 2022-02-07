// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner;

import static com.android.tools.r8.naming.retrace.StackTrace.isSame;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfRuntime;
import com.android.tools.r8.naming.retrace.StackTrace;
import com.android.tools.r8.naming.retrace.StackTrace.StackTraceLine;
import java.util.Objects;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SyntheticInlineNullCheckPositionTest extends TestBase {

  private static final StackTraceLine REQUIRE_NON_NULL_LINE =
      StackTraceLine.builder()
          .setClassName(Objects.class.getTypeName())
          .setMethodName("requireNonNull")
          .setFileName("Objects.java")
          .build();

  private final TestParameters parameters;
  private StackTrace expectedStackTraceWithGetClass;
  private StackTrace expectedStackTraceWithRequireNonNull;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return TestBase.getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Before
  public void setup() throws Exception {
    // Get the expected stack trace by running on the JVM.
    StackTrace actualStackTrace =
        testForJvm()
            .addTestClasspath()
            .run(CfRuntime.getSystemRuntime(), Main.class)
            .assertFailure()
            .map(StackTrace::extractFromJvm);
    expectedStackTraceWithGetClass = actualStackTrace;
    expectedStackTraceWithRequireNonNull =
        StackTrace.builder().add(REQUIRE_NON_NULL_LINE).add(actualStackTrace).build();
  }

  public SyntheticInlineNullCheckPositionTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepAttributeLineNumberTable()
        .addKeepAttributeSourceFile()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatThrows(NullPointerException.class)
        .inspectStackTrace(
            stackTrace -> assertThat(stackTrace, isSame(expectedStackTraceWithGetClass)));
  }

  static class Main {

    public static void main(String[] args) {
      A nullable = System.currentTimeMillis() < 0 ? new A() : null;
      nullable.m();
    }
  }

  static class A {

    void m() {
      System.out.println("Hello world!");
    }
  }
}
