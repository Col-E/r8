// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.retrace;

import static com.android.tools.r8.naming.retrace.StackTrace.isSame;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RetraceInlineeWithNullCheckTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean throwReceiverNpe;

  @Parameters(name = "{0}, throwReceiverNpe: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build(),
        BooleanUtils.values());
  }

  public StackTrace expectedStackTrace;

  private String[] getArgs() {
    return throwReceiverNpe ? new String[] {"foo"} : new String[0];
  }

  @Before
  public void setup() throws Exception {
    // Get the expected stack trace by running on the JVM/ART.
    expectedStackTrace =
        testForRuntime(parameters)
            .addProgramClasses(Caller.class, Foo.class)
            .run(parameters.getRuntime(), Caller.class, getArgs())
            .getStackTrace();
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Caller.class)
        .addKeepAttributeLineNumberTable()
        .addKeepAttributeSourceFile()
        .setMinApi(parameters.getApiLevel())
        .enableInliningAnnotations()
        .run(parameters.getRuntime(), Caller.class, getArgs())
        .assertFailureWithErrorThatThrows(NullPointerException.class)
        .inspectStackTrace(
            (stackTrace, codeInspector) -> assertThat(stackTrace, isSame(expectedStackTrace)));
  }

  static class Foo {
    @NeverInline
    Object notInlinable() {
      System.out.println("Hello, world!");
      throw new NullPointerException("Foo");
    }

    Object inlinable() {
      return notInlinable();
    }
  }

  static class Caller {
    @NeverInline
    static void caller(Foo f) {
      f.inlinable();
    }

    public static void main(String[] args) {
      caller(args.length == 0 ? new Foo() : null);
    }
  }
}
