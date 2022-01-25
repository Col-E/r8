// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.retrace;

import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.hamcrest.CoreMatchers;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RetraceInlineConditionTest extends TestBase {

  @Parameter() public TestParameters parameters;

  public StackTrace expectedStackTrace;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Before
  public void setup() throws Exception {
    // Get the expected stack trace by running on the JVM.
    TestBuilder<? extends SingleTestRunResult<?>, ?> testBuilder =
        testForRuntime(parameters).addInnerClasses(getClass());
    SingleTestRunResult<?> runResult = testBuilder.run(parameters.getRuntime(), Main.class);
    if (parameters.isCfRuntime()) {
      expectedStackTrace = runResult.map(StackTrace::extractFromJvm);
    } else {
      expectedStackTrace =
          StackTrace.extractFromArt(runResult.getStdErr(), parameters.asDexRuntime().getVm());
    }
  }

  @Test
  public void testR8() throws Throwable {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(Main.class)
        .compile()
        .inspectProguardMap(
            map -> {
              assertThat(
                  map, not(CoreMatchers.containsString("com.android.tools.r8.rewriteFrame")));
            });
  }

  static class Foo {

    void inlinable(boolean loop) {
      while (loop) {}
      String string = toString();
      System.out.println(string);
    }
  }

  static class Main {
    public static void main(String[] args) {
      Foo foo = (args.length == 0 ? null : new Foo());
      foo.inlinable(args.length == 0);
    }
  }
}
