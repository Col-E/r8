// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.code.invokedynamic;

import static org.junit.Assert.assertThrows;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.DiagnosticsMatcher;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.dex.Marker.Backend;
import com.android.tools.r8.errors.DexFileOverflowDiagnostic;
import com.android.tools.r8.errors.UnsupportedInvokeCustomDiagnostic;
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
  public void testD8DexNoDesugaring() {
    assertThrows(
        CompilationFailedException.class,
        () -> {
          testForD8(Backend.DEX)
              // DEPS contains all R8 dependencies, including guava, which extends the surface
              // of UnrepresentableRewriting.
              .addProgramFiles(ToolHelper.getDeps())
              .setMinApi(AndroidApiLevel.B)
              .disableDesugaring()
              .compileWithExpectedDiagnostics(
                  diagnostics ->
                      diagnostics
                          .assertAllWarningsMatch(
                              DiagnosticsMatcher.diagnosticType(
                                  UnsupportedInvokeCustomDiagnostic.class))
                          .assertErrorThatMatches(
                              DiagnosticsMatcher.diagnosticType(DexFileOverflowDiagnostic.class)));
        });
  }
}
