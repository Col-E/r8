// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.sealed;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.examples.jdk17.Sealed;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SealedAttributeTest extends TestBase {

  private final Backend backend;

  @Parameters(name = "{0}")
  public static List<Object[]> data() {
    // TODO(b/174431251): This should be replaced with .withCfRuntimes(start = jdk17).
    return buildParameters(
        getTestParameters().withCustomRuntime(TestRuntime.getCheckedInJdk17()).build(),
        Backend.values());
  }

  public SealedAttributeTest(TestParameters parameters, Backend backend) {
    this.backend = backend;
  }

  @Test
  public void testJvm() throws Exception {
    assumeTrue(backend == Backend.CF);
    testForJvm()
        .addRunClasspathFiles(Sealed.jar())
        .run(TestRuntime.getCheckedInJdk17(), Sealed.Main.typeName())
        .assertSuccessWithOutputLines("R8 compiler", "D8 compiler");
  }

  @Test
  public void testD8() throws Exception {
    assertThrows(
        CompilationFailedException.class,
        () -> {
          testForD8(backend)
              .addProgramFiles(Sealed.jar())
              .setMinApi(AndroidApiLevel.B)
              .compileWithExpectedDiagnostics(
                  diagnostics -> {
                    diagnostics.assertErrorThatMatches(
                        diagnosticMessage(containsString("Sealed classes are not supported")));
                  });
        });
  }

  @Test
  public void testR8() throws Exception {
    assertThrows(
        CompilationFailedException.class,
        () -> {
          testForR8(backend)
              .addProgramFiles(Sealed.jar())
              .setMinApi(AndroidApiLevel.B)
              .addKeepMainRule(Sealed.Main.typeName())
              .compileWithExpectedDiagnostics(
                  diagnostics -> {
                    diagnostics.assertErrorThatMatches(
                        diagnosticMessage(containsString("Sealed classes are not supported")));
                  });
        });
  }
}
