// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.checkcast;

import static org.junit.Assert.assertThrows;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/** This is a reproduction of b/160856783. */
@RunWith(Parameterized.class)
public class CheckCastNullForTypeTest extends TestBase {

  private final TestParameters parameters;
  private static final String EXPECTED = StringUtils.lines("null");

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public CheckCastNullForTypeTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(A.class, Main.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    assertThrows(
        CompilationFailedException.class,
        () ->
            testForR8(parameters.getBackend())
                .addProgramClasses(A.class, Main.class)
                .setMinApi(parameters.getApiLevel())
                .addKeepMainRule(Main.class)
                .enableInliningAnnotations()
                // TODO(b/160856783): This should not fail during compilation.
                .run(parameters.getRuntime(), Main.class)
                .assertSuccessWithOutput(EXPECTED));
  }

  public static class A {}

  public static class Main {

    public static void main(String[] args) {
      A a = null;
      Main main;
      do {
        main = (Main) (Object) a;
      } while ((a = (A) null) != null);
      System.out.println(a);
    }
  }
}
