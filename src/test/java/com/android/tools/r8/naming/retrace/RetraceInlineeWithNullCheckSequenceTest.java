// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.retrace;

import static com.android.tools.r8.naming.retrace.StackTrace.isSame;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RetraceInlineeWithNullCheckSequenceTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  public StackTrace expectedStackTrace;

  @Before
  public void setup() throws Exception {
    // Get the expected stack trace by running on the JVM.
    expectedStackTrace =
        testForRuntime(parameters)
            .addProgramClasses(Caller.class, Foo.class)
            .run(parameters.getRuntime(), Caller.class)
            .assertFailureWithErrorThatThrows(NullPointerException.class)
            .getStackTrace();
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Caller.class)
        .addKeepAttributeLineNumberTable()
        .addKeepAttributeSourceFile()
        .setMinApi(parameters)
        .enableInliningAnnotations()
        .enableExperimentalMapFileVersion()
        .run(parameters.getRuntime(), Caller.class)
        .assertFailureWithErrorThatThrows(NullPointerException.class)
        .inspectStackTrace(
            (stackTrace, codeInspector) -> {
              assertThat(stackTrace, isSame(expectedStackTrace));
            });
  }

  static class Foo {

    @NeverInline
    void notInlinable() {
      System.out.println("Hello, world!");
      throw new RuntimeException("Foo");
    }

    void inlinable1() {
      notInlinable();
    }

    void inlinable2() {
      inlinable1();
    }

    void inlinable3() {
      inlinable2();
    }
  }

  static class Caller {

    @NeverInline
    static void caller(Foo f) {
      f.inlinable3();
    }

    public static void main(String[] args) {
      caller(System.currentTimeMillis() < 0 ? new Foo() : null);
    }
  }
}
