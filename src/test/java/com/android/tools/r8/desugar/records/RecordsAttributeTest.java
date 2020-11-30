// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.records;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfRuntime;
import com.android.tools.r8.examples.jdk15.Records;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RecordsAttributeTest extends TestBase {

  private final Backend backend;
  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static List<Object[]> data() {
    // TODO(b/174431251): This should be replaced with .withCfRuntimes(start = jdk15).
    return buildParameters(
        getTestParameters().withCustomRuntime(CfRuntime.getCheckedInJdk15()).build(),
        Backend.values());
  }

  public RecordsAttributeTest(TestParameters parameters, Backend backend) {
    this.parameters = parameters;
    this.backend = backend;
  }

  @Test
  public void testJvm() throws Exception {
    assumeFalse(parameters.isNoneRuntime());
    assumeTrue(backend == Backend.CF);
    testForJvm()
        .addRunClasspathFiles(Records.jar())
        .addVmArguments("--enable-preview")
        .run(parameters.getRuntime(), Records.Main.typeName())
        .assertSuccessWithOutputLines("Jane Doe", "42");
  }

  @Test
  public void testD8() throws Exception {
    assertThrows(
        CompilationFailedException.class,
        () -> {
          testForD8(backend)
              .addProgramClassFileData(Records.Main.bytes(), Records.Main$Person.bytes())
              .setMinApi(AndroidApiLevel.B)
              .compileWithExpectedDiagnostics(
                  diagnostics -> {
                    diagnostics.assertErrorThatMatches(
                        diagnosticMessage(containsString("Records are not supported")));
                  });
        });
  }

  @Test
  public void testR8() throws Exception {
    assertThrows(
        CompilationFailedException.class,
        () -> {
          testForR8(backend)
              .addProgramClassFileData(Records.Main.bytes(), Records.Main$Person.bytes())
              .setMinApi(AndroidApiLevel.B)
              .addKeepMainRule(Records.Main.typeName())
              .compileWithExpectedDiagnostics(
                  diagnostics -> {
                    diagnostics.assertErrorThatMatches(
                        diagnosticMessage(containsString("Records are not supported")));
                  });
        });
  }
}
