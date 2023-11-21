// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.records;


import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class EmptyRecordAnnotationTest extends TestBase {

  private static final String RECORD_NAME = "EmptyRecordAnnotation";
  private static final byte[][] PROGRAM_DATA = RecordTestUtils.getProgramData(RECORD_NAME);
  private static final String MAIN_TYPE = RecordTestUtils.getMainType(RECORD_NAME);
  private static final String EXPECTED_RESULT_NATIVE_RECORD =
      StringUtils.lines("class java.lang.Record", "class records.EmptyRecordAnnotation$Empty");
  private static final String EXPECTED_RESULT_DESUGARED_RECORD =
      StringUtils.lines(
          "class com.android.tools.r8.RecordTag", "class records.EmptyRecordAnnotation$Empty");

  private final TestParameters parameters;

  public EmptyRecordAnnotationTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK17)
        .withDexRuntimes()
        .withAllApiLevelsAlsoForCf()
        .build();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClassFileData(PROGRAM_DATA)
        .run(parameters.getRuntime(), MAIN_TYPE)
        .assertSuccessWithOutput(EXPECTED_RESULT_NATIVE_RECORD);
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters.getBackend())
        .addProgramClassFileData(PROGRAM_DATA)
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), MAIN_TYPE)
        .applyIf(
            isRecordsDesugaredForD8(parameters),
            r -> r.assertSuccessWithOutput(EXPECTED_RESULT_DESUGARED_RECORD),
            r -> r.assertSuccessWithOutput(EXPECTED_RESULT_NATIVE_RECORD));
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .addLibraryFiles(RecordTestUtils.getJdk15LibraryFiles(temp))
        .addProgramClassFileData(PROGRAM_DATA)
        .setMinApi(parameters)
        .addKeepRules("-keep class records.EmptyRecordAnnotation { *; }")
        .addKeepRules("-keepattributes *Annotation*")
        .addKeepRules("-keep class records.EmptyRecordAnnotation$Empty")
        .addKeepMainRule(MAIN_TYPE)
        // This is used to avoid renaming com.android.tools.r8.RecordTag.
        .applyIf(
            isRecordsDesugaredForR8(parameters),
            b -> b.addKeepRules("-keep class java.lang.Record"))
        .compile()
        .applyIf(parameters.isCfRuntime(), r -> r.inspect(RecordTestUtils::assertRecordsAreRecords))
        .run(parameters.getRuntime(), MAIN_TYPE)
        .applyIf(
            isRecordsDesugaredForR8(parameters),
            r -> r.assertSuccessWithOutput(EXPECTED_RESULT_DESUGARED_RECORD),
            r -> r.assertSuccessWithOutput(EXPECTED_RESULT_NATIVE_RECORD));
  }
}
