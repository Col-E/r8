// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticException;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TestingAssertionsTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public TestingAssertionsTest(TestParameters parameters) {}

  @Test(expected = CompilationFailedException.class)
  public void testR8() throws Exception {
    testForR8(Backend.DEX)
        .addInnerClasses(getClass())
        .setMinApi(AndroidApiLevel.B)
        .addKeepMainRule(Main.class)
        .addOptionsModification(
            options -> {
              assertTrue(options.testing.enableTestAssertions);
              options.testing.testEnableTestAssertions = true;
            })
        .compileWithExpectedDiagnostics(
            diagnostics -> {
              diagnostics.assertErrorThatMatches(diagnosticException(AssertionError.class));
            });
  }

  @Test(expected = CompilationFailedException.class)
  public void testD8() throws Exception {
    testForD8(Backend.DEX)
        .addInnerClasses(getClass())
        .setMinApi(AndroidApiLevel.B)
        .addOptionsModification(
            options -> {
              assertTrue(options.testing.enableTestAssertions);
              options.testing.testEnableTestAssertions = true;
            })
        .compileWithExpectedDiagnostics(
            diagnostics -> {
              diagnostics.assertErrorThatMatches(diagnosticException(AssertionError.class));
            });
  }

  public static class Main {

    public static void main(String[] args) {
      System.out.println("Hello World");
    }
  }
}
