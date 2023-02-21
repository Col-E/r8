// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.retrace;

import static com.android.tools.r8.naming.retrace.StackTrace.isSame;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoMethodStaticizing;
import com.android.tools.r8.SingleTestRunResult;
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
public class RetraceInlineeWithNullCheckFollowingImplicitReceiverNullCheckTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  public StackTrace expectedStackTrace;

  @Before
  public void setup() throws Exception {
    expectedStackTrace = getStackTrace();
  }

  private StackTrace getStackTrace(String... args) throws Exception {
    SingleTestRunResult<?> runResult =
        testForRuntime(parameters)
            .addProgramClasses(Caller.class, Foo.class)
            .run(parameters.getRuntime(), Caller.class, args);
    return parameters.isCfRuntime()
        ? runResult.map(StackTrace::extractFromJvm)
        : StackTrace.extractFromArt(runResult.getStdErr(), parameters.asDexRuntime().getVm());
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Caller.class)
        .addKeepAttributeLineNumberTable()
        .addKeepAttributeSourceFile()
        .enableExperimentalMapFileVersion()
        .enableInliningAnnotations()
        .enableNoMethodStaticizingAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Caller.class)
        .assertFailureWithErrorThatThrows(NullPointerException.class)
        .inspectStackTrace(
            (stackTrace, codeInspector) -> assertThat(stackTrace, isSame(expectedStackTrace)));
  }

  static class Foo {

    @NeverInline
    @NoMethodStaticizing
    void checkNull() {
      System.out.println("Hello, world!");
    }

    void inlinable(Foo foo) {
      checkNull();
      foo.checkNull();
    }
  }

  static class Caller {

    @NeverInline
    static void caller(Foo f) {
      f.inlinable(null);
    }

    public static void main(String[] args) {
      caller(args.length == 0 ? new Foo() : null);
    }
  }
}
