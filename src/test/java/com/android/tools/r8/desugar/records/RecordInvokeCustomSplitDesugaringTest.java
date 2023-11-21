// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.records;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.errors.DuplicateTypeInProgramAndLibraryDiagnostic;
import com.android.tools.r8.errors.DuplicateTypesDiagnostic;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.ZipUtils;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RecordInvokeCustomSplitDesugaringTest extends TestBase {

  private static final String RECORD_NAME = "RecordInvokeCustom";
  private static final byte[][] PROGRAM_DATA = RecordTestUtils.getProgramData(RECORD_NAME);
  private static final String MAIN_TYPE = RecordTestUtils.getMainType(RECORD_NAME);
  private static final String EXPECTED_RESULT =
      StringUtils.lines(
          "%s[]",
          "true",
          "true",
          "true",
          "true",
          "true",
          "false",
          "true",
          "true",
          "false",
          "false",
          "%s[%s=Jane Doe, %s=42]");
  private static final String EXPECTED_RESULT_D8 =
      String.format(EXPECTED_RESULT, "Empty", "Person", "name", "age");
  private static final String EXPECTED_RESULT_R8 =
      String.format(EXPECTED_RESULT, "a", "b", "name", "age");

  private final TestParameters parameters;

  public RecordInvokeCustomSplitDesugaringTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimesAndAllApiLevels().build();
  }

  @Test
  public void testD8() throws Exception {
    Path desugared =
        testForD8(Backend.CF)
            .addProgramClassFileData(PROGRAM_DATA)
            .setMinApi(parameters)
            .compile()
            .writeToZip();
    testForD8(parameters.getBackend())
        .addProgramFiles(desugared)
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), MAIN_TYPE)
        .assertSuccessWithOutput(EXPECTED_RESULT_D8);
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    Path desugared =
        testForD8(Backend.CF)
            .addProgramClassFileData(PROGRAM_DATA)
            .setMinApi(parameters)
            .compile()
            .writeToZip();
    if (isRecordsDesugaredForD8(parameters)) {
      assertTrue(ZipUtils.containsEntry(desugared, "com/android/tools/r8/RecordTag.class"));
    } else {
      assertFalse(ZipUtils.containsEntry(desugared, "com/android/tools/r8/RecordTag.class"));
    }
    testForR8(parameters.getBackend())
        .addProgramFiles(desugared)
        .setMinApi(parameters)
        .addKeepMainRule(MAIN_TYPE)
        .allowDiagnosticMessages()
        .compileWithExpectedDiagnostics(
            // Class com.android.tools.r8.RecordTag in desugared input is seen as java.lang.Record
            // when reading causing the duplicate class.
            diagnostics -> {
              if (parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.U)) {
                diagnostics
                    .assertNoErrors()
                    .assertInfosMatch(
                        allOf(
                            diagnosticType(DuplicateTypesDiagnostic.class),
                            diagnosticType(DuplicateTypeInProgramAndLibraryDiagnostic.class),
                            diagnosticMessage(containsString("java.lang.Record"))))
                    .assertWarningsMatch(
                        allOf(
                            diagnosticType(StringDiagnostic.class),
                            diagnosticMessage(containsString("java.lang.Record"))));
              } else {
                diagnostics.assertNoMessages();
              }
            })
        .run(parameters.getRuntime(), MAIN_TYPE)
        .assertSuccessWithOutput(EXPECTED_RESULT_R8);
  }
}
