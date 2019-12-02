// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DisableDesugarTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public DisableDesugarTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private void checkExpectedDiagnostics(TestDiagnosticMessages messages) {
    messages.assertInfosCount(0);
    messages.assertWarningsCount(0);
    messages.assertErrorsCount(1);
    assertThat(
        messages.getErrors().get(0).getDiagnosticMessage(),
        containsString("Using desugared library configuration requires desugaring to be enabled"));
  }

  @Test
  public void testDisableDesugarD8() throws Exception {
    try {
      testForD8()
          .addInnerClasses(DisableDesugarTest.class)
          .setMinApi(parameters.getApiLevel())
          .noDesugaring()
          .enableCoreLibraryDesugaring(AndroidApiLevel.B)
          .compileWithExpectedDiagnostics(this::checkExpectedDiagnostics);
    } catch (CompilationFailedException e) {
      // Expected compilation failed.
      return;
    }
    fail("Expected test to fail with CompilationFailedException");
  }

  @Test
  public void testDisableDesugarR8() throws Exception {
    try {
      testForR8(parameters.getBackend())
          .addInnerClasses(DisableDesugarTest.class)
          .addKeepMainRule(TestClass.class)
          .setMinApi(parameters.getApiLevel())
          .noDesugaring()
          .enableCoreLibraryDesugaring(AndroidApiLevel.B)
          .compileWithExpectedDiagnostics(this::checkExpectedDiagnostics);
    } catch (CompilationFailedException e) {
      // Expected compilation failed.
      return;
    }
    fail("Expected test to fail with CompilationFailedException");
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println("Hello, world");
    }
  }
}
