// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticException;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KeepAnnotatedMemberTest extends TestBase {

  private static final Path R8_JAR = Paths.get(ToolHelper.THIRD_PARTY_DIR, "r8", "r8.jar");
  private static final String ABSENT_ANNOTATION = "com.android.tools.r8.MissingAnnotation";
  private static final String PRESENT_ANNOTATION =
      "com.android.tools.r8.com.google.common.annotations.VisibleForTesting";

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public KeepAnnotatedMemberTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void testPresence() throws Exception {
    CodeInspector inspector = new CodeInspector(R8_JAR);
    assertThat(inspector.clazz(ABSENT_ANNOTATION), not(isPresent()));
    assertThat(inspector.clazz(PRESENT_ANNOTATION), isPresent());
  }

  // TODO(b/159966986): Fix this!
  @Test(expected = CompilationFailedException.class)
  public void testAbsentAnnotation() throws Exception {
    testForR8(Backend.CF)
        .addProgramFiles(R8_JAR)
        .addKeepRules("-keep class * { @" + ABSENT_ANNOTATION + " *; }")
        .setMinApi(10000)
        .compileWithExpectedDiagnostics(
            diagnostics ->
                diagnostics.assertErrorsMatch(diagnosticException(AssertionError.class)));
  }

  // TODO(b/159966986): Fix this!
  @Test(expected = CompilationFailedException.class)
  public void testPresentAnnotation() throws Exception {
    testForR8(Backend.CF)
        .addProgramFiles(Paths.get(ToolHelper.THIRD_PARTY_DIR, "r8", "r8.jar"))
        .addKeepRules("-keep class * { @" + PRESENT_ANNOTATION + " *; }")
        .setMinApi(10000)
        .compileWithExpectedDiagnostics(
            diagnostics ->
                diagnostics.assertErrorsMatch(diagnosticException(AssertionError.class)));
  }
}
