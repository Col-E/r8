// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.enumunboxing;

import static org.junit.Assert.assertThrows;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.DiagnosticsMatcher;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** This is a reproducation of b/245096779 */
@RunWith(Parameterized.class)
public class PrivateEnumWithPrivateInvokeSpecialTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testR8() throws Exception {
    R8FullTestBuilder r8FullTestBuilder =
        testForR8(parameters.getBackend())
            .addInnerClasses(getClass())
            .setMinApi(parameters.getApiLevel())
            .addKeepMainRule(Main.class);
    if (parameters.isCfRuntime()) {
      // TODO(b/245096779): Should not throw an error.
      assertThrows(
          CompilationFailedException.class,
          () -> {
            r8FullTestBuilder.compileWithExpectedDiagnostics(
                diagnostics ->
                    diagnostics.assertErrorThatMatches(
                        DiagnosticsMatcher.diagnosticException(AssertionError.class)));
          });
    } else {
      r8FullTestBuilder
          .run(parameters.getRuntime(), Main.class)
          .assertSuccessWithOutputLines("FOO");
    }
  }

  private static class Main {

    private enum MyEnum {
      FOO,
      BAR;

      private boolean isFoo() {
        return this == FOO;
      }

      public MyEnum compute() {
        if (isFoo()) {
          return BAR;
        }
        return FOO;
      }
    }

    public static void main(String[] args) {
      MyEnum myEnum = args.length > 0 ? MyEnum.FOO : MyEnum.BAR;
      if (myEnum.compute().isFoo()) {
        System.out.println("FOO");
      } else {
        System.out.println("BAR");
      }
    }
  }
}
