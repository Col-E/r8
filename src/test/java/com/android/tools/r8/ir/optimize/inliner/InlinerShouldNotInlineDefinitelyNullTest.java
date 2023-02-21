// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/** This is a small reproduction of b/140851070 */
@RunWith(Parameterized.class)
public class InlinerShouldNotInlineDefinitelyNullTest extends TestBase {

  public static class A {

    void foo() {
      System.out.println("This will never be called");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      A a = null;
      a.foo(); // <-- will insert empty throwing code because a is definitely null.
    }
  }

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public InlinerShouldNotInlineDefinitelyNullTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void ensureThrowNullInliningsHaveInlinePositions()
      throws CompilationFailedException, IOException, ExecutionException {
    R8TestRunResult result =
        testForR8(parameters.getBackend())
            .addProgramClasses(Main.class, A.class)
            .addKeepMainRule(Main.class)
            .addKeepAllAttributes()
            .addKeepClassRules(A.class)
            .setMinApi(parameters)
            .compile()
            .inspect(
                inspector -> {
                  // Do a manual check that the invoke to foo is replaced by throw.
                  assertTrue(
                      inspector
                          .clazz(Main.class)
                          .uniqueMethodWithOriginalName("main")
                          .streamInstructions()
                          .anyMatch(InstructionSubject::isThrow));
                })
            .run(parameters.getRuntime(), Main.class)
            .assertFailureWithErrorThatMatches(containsString("java.lang.NullPointerException"))
            .inspectStackTrace(
                stackTrace -> {
                  assertThat(
                      stackTrace.toString(), containsString(Main.class.getTypeName() + ".main("));
                });
    String[] split = result.proguardMap().split("\n");
    assertTrue(Arrays.stream(split).noneMatch(l -> l.contains(A.class.getTypeName() + ".foo")));
  }
}
