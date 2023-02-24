// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf.stackmap;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.transformers.ClassFileTransformer.MethodPredicate;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InvalidLongStackValueMaxHeightTest extends TestBase {

  private final String[] EXPECTED = new String[] {"52"};
  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  public InvalidLongStackValueMaxHeightTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void smokeTest() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(Main.class, Tester.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test(expected = CompilationFailedException.class)
  public void testD8Cf() throws Exception {
    parameters.assumeCfRuntime();
    testForD8(parameters.getBackend())
        .addProgramClasses(Tester.class)
        .addProgramClassFileData(getMainWithChangedMaxStackHeight())
        .setMinApi(parameters)
        .compileWithExpectedDiagnostics(
            diagnostics -> {
              diagnostics.assertWarningThatMatches(
                  diagnosticMessage(containsString("The max stack height of 2 is violated")));
            });
  }

  @Test()
  public void testD8Dex() throws Exception {
    parameters.assumeDexRuntime();
    testForD8()
        .addProgramClasses(Tester.class)
        .addProgramClassFileData(getMainWithChangedMaxStackHeight())
        .setMinApi(parameters)
        .compileWithExpectedDiagnostics(
            diagnostics -> {
              diagnostics.assertWarningThatMatches(
                  diagnosticMessage(containsString("The max stack height of 2 is violated")));
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  public byte[] getMainWithChangedMaxStackHeight() throws Exception {
    return transformer(Main.class).setMaxStackHeight(MethodPredicate.onName("main"), 2).transform();
  }

  public static class Tester {

    public static void test(long x, int y) {
      System.out.println(x + y);
    }
  }

  public static class Main {

    public static void main(String[] args) {
      Tester.test(10L, 42);
    }
  }
}
