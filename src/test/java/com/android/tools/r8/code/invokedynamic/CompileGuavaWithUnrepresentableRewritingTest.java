// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.code.invokedynamic;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** This is a regression test for b/238175192 */
@RunWith(Parameterized.class)
public class CompileGuavaWithUnrepresentableRewritingTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Test
  public void testD8DexNoDesugaring() throws Throwable {
    assumeTrue(ToolHelper.isTestingR8Lib());
    // TODO(b/238175192): We should not generate invalid bytecode
    assertThrows(
        CompilationFailedException.class,
        () -> {
          testForD8(Backend.DEX)
              .addProgramFiles(ToolHelper.DEPS)
              .setMinApi(AndroidApiLevel.B)
              .disableDesugaring()
              .mapUnsupportedFeaturesToWarnings()
              // TODO(b/238175192): remove again when resolved
              .addOptionsModification(
                  options -> options.enableUnrepresentableInDexInstructionRemoval = true)
              .compileWithExpectedDiagnostics(
                  diagnostics ->
                      diagnostics
                          .assertErrorMessageThatMatches(
                              containsString("Expected stack type to be single width"))
                          .assertWarningMessageThatMatches(
                              containsString("Invalid stack map table at instruction")));
        });
  }
}
