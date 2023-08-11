// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.outliner;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThrows;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** This is a reproduction of b/295136314. */
@RunWith(Parameterized.class)
public class OutlineWithoutThrowingInstructionTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testR8() throws Exception {
    // TODO(b/295136314): Account for no positions in outline caller.
    assertThrows(
        CompilationFailedException.class,
        () ->
            testForR8(parameters.getBackend())
                .addInnerClasses(getClass())
                .setMinApi(parameters)
                .addKeepMainRule(Main.class)
                .enableInliningAnnotations()
                .addOptionsModification(
                    options -> {
                      options.outline.threshold = 2;
                      options.outline.minSize = 2;
                    })
                .addKeepAttributeLineNumberTable()
                .addKeepAttributeSourceFile()
                .compileWithExpectedDiagnostics(
                    diagnostics ->
                        diagnostics.assertErrorMessageThatMatches(
                            containsString("Mapped outline positions is empty"))));
  }

  public static class Main {

    public static void main(String[] args) {
      int x = System.currentTimeMillis() > 0 ? 1 : 4;
      int y = System.currentTimeMillis() > 0 ? 2 : 5;
      int z = 3;
      System.out.println(foo(x, y, z) + bar(x, y, z));
    }

    @NeverInline
    public static int foo(int x, int y, int z) {
      return 1 + (x + y) / z;
    }

    @NeverInline
    public static int bar(int x, int y, int z) {
      return 2 + (x + y) / z;
    }
  }
}
