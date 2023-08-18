// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.code.invokedynamic;

import static org.hamcrest.CoreMatchers.anyOf;

import com.android.tools.r8.DiagnosticsMatcher;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.errors.UnsupportedInvokeCustomDiagnostic;
import com.android.tools.r8.errors.UnsupportedInvokePolymorphicMethodHandleDiagnostic;
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
  public void testD8DexNoDesugaring() throws Exception {
    testForD8(Backend.DEX)
        .addProgramFiles(ToolHelper.GUAVA_JRE)
        .setMinApi(AndroidApiLevel.N)
        .disableDesugaring()
        .compileWithExpectedDiagnostics(
            diagnostics ->
                diagnostics
                    .assertHasWarnings()
                    .assertAllWarningsMatch(
                        anyOf(
                            DiagnosticsMatcher.diagnosticType(
                                UnsupportedInvokeCustomDiagnostic.class),
                            DiagnosticsMatcher.diagnosticType(
                                UnsupportedInvokePolymorphicMethodHandleDiagnostic.class)))
                    .assertNoErrors());
  }
}
