// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.records;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThrows;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfRuntime;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.util.List;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/** Remove this test when Records are supported by default. */
@RunWith(Parameterized.class)
public class InvalidRecordAttributeTest extends TestBase {

  private final TestParameters parameters;
  private final Backend backend;

  private static final String EMPTY_RECORD = "EmptyRecord";
  private static final byte[][] EMPTY_RECORD_PROGRAM_DATA =
      RecordTestUtils.getProgramData(EMPTY_RECORD);
  private static final String SIMPLE_RECORD = "SimpleRecord";
  private static final byte[][] SIMPLE_RECORD_PROGRAM_DATA =
      RecordTestUtils.getProgramData(SIMPLE_RECORD);

  @Parameters(name = "{0} back: {1}")
  public static List<Object[]> data() {
    // TODO(b/174431251): This should be replaced with .withCfRuntimes(start = jdk16).
    return buildParameters(
        getTestParameters().withCustomRuntime(CfRuntime.getCheckedInJdk16()).build(),
        Backend.values());
  }

  public InvalidRecordAttributeTest(TestParameters parameters, Backend backend) {
    this.parameters = parameters;
    this.backend = backend;
  }

  @Test
  public void testD8EmptyRecord() throws Exception {
    Assume.assumeTrue(backend.isDex());
    assertThrows(
        CompilationFailedException.class,
        () -> {
          testForD8(backend)
              .addProgramClassFileData(EMPTY_RECORD_PROGRAM_DATA)
              .setMinApi(AndroidApiLevel.B)
              .compileWithExpectedDiagnostics(
                  InvalidRecordAttributeTest::assertUnsupportedRecordError);
        });
  }

  @Test
  public void testD8SimpleRecord() throws Exception {
    Assume.assumeTrue(backend.isDex());
    assertThrows(
        CompilationFailedException.class,
        () -> {
          testForD8(backend)
              .addProgramClassFileData(RecordTestUtils.getProgramData(SIMPLE_RECORD))
              .setMinApi(AndroidApiLevel.B)
              .compileWithExpectedDiagnostics(
                  InvalidRecordAttributeTest::assertUnsupportedRecordError);
        });
  }

  @Test
  public void testR8EmptyRecord() throws Exception {
    assertThrows(
        CompilationFailedException.class,
        () -> {
          testForR8(backend)
              .addProgramClassFileData(EMPTY_RECORD_PROGRAM_DATA)
              .setMinApi(AndroidApiLevel.B)
              .addKeepMainRule(RecordTestUtils.getMainType(EMPTY_RECORD))
              .compileWithExpectedDiagnostics(
                  InvalidRecordAttributeTest::assertUnsupportedRecordError);
        });
  }

  @Test
  public void testR8SimpleRecord() throws Exception {
    assertThrows(
        CompilationFailedException.class,
        () -> {
          testForR8(backend)
              .addProgramClassFileData(SIMPLE_RECORD_PROGRAM_DATA)
              .setMinApi(AndroidApiLevel.B)
              .addKeepMainRule(RecordTestUtils.getMainType(SIMPLE_RECORD))
              .compileWithExpectedDiagnostics(
                  InvalidRecordAttributeTest::assertUnsupportedRecordError);
        });
  }

  private static void assertUnsupportedRecordError(TestDiagnosticMessages diagnostics) {
    diagnostics.assertErrorThatMatches(
        diagnosticMessage(containsString("Records are not supported")));
  }
}
