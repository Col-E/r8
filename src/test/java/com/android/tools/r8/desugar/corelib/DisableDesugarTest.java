// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.corelib;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DisableDesugarTest extends CoreLibDesugarTestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public DisableDesugarTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testDisableDesugarD8() throws Exception {
    try {
      testForD8()
          .addInnerClasses(DisableDesugarTest.class)
          .setMinApi(parameters.getApiLevel())
          .noDesugaring()
          .enableCoreLibraryDesugaring(AndroidApiLevel.B)
          .compile()
          .assertNoMessages();
    } catch (CompilationFailedException e) {
      assertThat(
          e.getCause().getMessage(),
          containsString("Using special library configuration requires desugaring to be enabled"));
    }
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
          .compile()
          .assertNoMessages();
    } catch (CompilationFailedException e) {
      assertThat(
          e.getCause().getMessage(),
          containsString("Using special library configuration requires desugaring to be enabled"));
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println("Hello, world");
    }
  }
}
