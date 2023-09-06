// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.records;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.errors.DuplicateTypeInProgramAndLibraryDiagnostic;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
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
          "%s[name=Jane Doe, age=42]");
  private static final String EXPECTED_RESULT_D8 =
      String.format(EXPECTED_RESULT, "Empty", "Person");
  private static final String EXPECTED_RESULT_R8 = String.format(EXPECTED_RESULT, "a", "b");

  private final TestParameters parameters;

  public RecordInvokeCustomSplitDesugaringTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevelsAlsoForCf().build();
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
    testForR8(parameters.getBackend())
        .addProgramFiles(desugared)
        .setMinApi(parameters)
        .addKeepMainRule(MAIN_TYPE)
        .applyIf(
            parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.U),
            b -> b.allowDiagnosticMessages())
        .compileWithExpectedDiagnostics(
            // Type com.android.tools.r8.RecordTag from desugared code will be converted to
            // java.lang.Record during reading causing duplicate java.lang.Record class.
            parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.U)
                ? diagnostics ->
                    diagnostics
                        .assertWarningsMatch(
                            diagnosticMessage(
                                containsString(
                                    "The following library types, prefixed by java., are present"
                                        + " both as library and non library classes:"
                                        + " java.lang.Record.")))
                        .assertInfosMatch(
                            allOf(
                                diagnosticType(DuplicateTypeInProgramAndLibraryDiagnostic.class),
                                diagnosticMessage(containsString("java.lang.Record"))))
                        .assertNoErrors()
                : diagnostics -> diagnostics.assertNoMessages())
        .run(parameters.getRuntime(), MAIN_TYPE)
        .assertSuccessWithOutput(EXPECTED_RESULT_R8);
  }
}
