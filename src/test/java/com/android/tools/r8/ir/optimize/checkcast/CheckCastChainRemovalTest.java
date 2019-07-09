// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.checkcast;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringContains.containsString;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.NeverInline;
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
public class CheckCastChainRemovalTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().build();
  }

  public CheckCastChainRemovalTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  static class A {}

  static class B extends A {}

  public static class MainDescendingRunner {

    public static void main(String[] args) {
      Object foo = foo();
      B b = (B) foo;
      A a = (A) b;
      System.out.println("Should have failed at this point: " + a);
    }

    @NeverInline
    private static Object foo() {
      if (System.currentTimeMillis() > 0) {
        return "foo";
      } else {
        return 42;
      }
    }
  }

  public static class MainAscendingRunner {

    public static void main(String[] args) {
      Object foo = foo();
      A a = (A) foo;
      B b = (B) a;
      System.out.println("Should have failed at this point: " + b);
    }

    @NeverInline
    private static Object foo() {
      if (System.currentTimeMillis() > 0) {
        return "foo";
      } else {
        return 42;
      }
    }
  }

  @Test
  public void testDescendingChainWillProvideCorrectStacktrace()
      throws IOException, CompilationFailedException, ExecutionException {
    String stdErr =
        testForR8(parameters.getBackend())
            .addProgramClasses(A.class, B.class, MainDescendingRunner.class)
            .addKeepAllClassesRule()
            .addKeepMainRule(MainDescendingRunner.class)
            .enableInliningAnnotations()
            .setMinApi(parameters.getRuntime())
            .run(parameters.getRuntime(), MainDescendingRunner.class)
            .assertFailure()
            .getStdErr();
    assertThat(stdErr, containsString("ClassCastException"));
    assertThat(
        stdErr,
        containsString("com.android.tools.r8.ir.optimize.checkcast.CheckCastChainRemovalTest$B"));
  }

  @Test
  public void testAscendingChainWillProvideCorrectStacktrace()
      throws IOException, CompilationFailedException, ExecutionException {
    String stdErr =
        testForR8(parameters.getBackend())
            .addProgramClasses(A.class, B.class, MainAscendingRunner.class)
            .addKeepAllClassesRule()
            .addKeepMainRule(MainAscendingRunner.class)
            .enableInliningAnnotations()
            .setMinApi(parameters.getRuntime())
            .run(parameters.getRuntime(), MainAscendingRunner.class)
            .assertFailure()
            .getStdErr();
    assertThat(stdErr, containsString("ClassCastException"));
    assertThat(
        stdErr,
        containsString("com.android.tools.r8.ir.optimize.checkcast.CheckCastChainRemovalTest$A"));
  }
}
