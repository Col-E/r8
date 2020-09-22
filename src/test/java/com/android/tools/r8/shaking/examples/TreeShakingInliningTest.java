// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.examples;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThrows;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestCompilerBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.shaking.TreeShakingTest;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TreeShakingInliningTest extends TreeShakingTest {

  @Parameters(name = "mode:{0}-{1} minify:{2}")
  public static List<Object[]> data() {
    return defaultTreeShakingParameters();
  }

  public TreeShakingInliningTest(Frontend frontend, TestParameters parameters, MinifyMode minify) {
    super(frontend, parameters, minify);
  }

  @Override
  protected String getName() {
    return "examples/inlining";
  }

  @Override
  protected String getMainClass() {
    return "inlining.Inlining";
  }

  @Test
  public void testKeeprules() throws Exception {
    runTest(null, null, null, ImmutableList.of("src/test/examples/inlining/keep-rules.txt"), null);
  }

  @Test
  public void testKeeprulesdiscard() throws Exception {
    // On the cf backend, we don't inline into constructors, see: b/136250031
    List<String> keepRules =
        getParameters().isCfRuntime()
            ? ImmutableList.of("src/test/examples/inlining/keep-rules-discard.txt")
            : ImmutableList.of(
                "src/test/examples/inlining/keep-rules-discard.txt",
                "src/test/examples/inlining/keep-rules-discard-constructor.txt");
    if (getParameters().isDexRuntime()
        && getParameters().getApiLevel().isLessThan(AndroidApiLevel.K)) {
      // We fail to inline due to Objects.requireNonNull is missing in Android.jar before K.
      assertThrows(
          CompilationFailedException.class,
          () ->
              runTest(
                  null,
                  null,
                  null,
                  keepRules,
                  null,
                  TestCompilerBuilder::allowStderrMessages,
                  diagnostics ->
                      diagnostics.assertAllErrorsMatch(
                          diagnosticMessage(containsString("Discard checks failed")))));
    } else {
      runTest(null, null, null, keepRules, null, TestCompilerBuilder::allowStderrMessages, null);
    }
  }
}
