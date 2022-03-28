// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.records;

import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.utils.InternalOptions.TestingOptions;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RecordLibMergeTest extends TestBase {

  private static final String RECORD_LIB = "RecordLib";
  private static final String RECORD_MAIN = "RecordMain";
  private static final byte[][] PROGRAM_DATA_LIB = RecordTestUtils.getProgramData(RECORD_LIB);
  private static final byte[][] PROGRAM_DATA_MAIN = RecordTestUtils.getProgramData(RECORD_MAIN);
  private static final String MAIN_TYPE = RecordTestUtils.getMainType(RECORD_MAIN);
  private static final String EXPECTED_RESULT = StringUtils.lines("true", "true");

  private final TestParameters parameters;

  public RecordLibMergeTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Parameterized.Parameters(name = "{0}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters()
            .withCfRuntimesStartingFromIncluding(CfVm.JDK17)
            .withDexRuntimes()
            .withAllApiLevelsAlsoForCf()
            .build());
  }

  @Test
  public void testR8Merge() throws Exception {
    Path lib =
        testForR8(Backend.CF)
            .addProgramClassFileData(PROGRAM_DATA_LIB)
            .setMinApi(parameters.getApiLevel())
            .addKeepRules(
                "-keep class records.RecordLib { public static java.lang.Object getRecord(); }")
            .addKeepRules("-keep class records.RecordLib$LibRecord")
            .addLibraryFiles(RecordTestUtils.getJdk15LibraryFiles(temp))
            .addOptionsModification(TestingOptions::allowExperimentClassFileVersion)
            .compile()
            .writeToZip();
    R8FullTestBuilder builder =
        testForR8(parameters.getBackend())
            .addProgramFiles(lib)
            .addProgramClassFileData(PROGRAM_DATA_MAIN)
            .setMinApi(parameters.getApiLevel())
            .addKeepMainRule(MAIN_TYPE)
            .addKeepRules("-keep class records.RecordLib$LibRecord")
            .addKeepRules("-keep class records.RecordMain$MainRecord")
            .addOptionsModification(TestingOptions::allowExperimentClassFileVersion);
    if (parameters.isCfRuntime()) {
      builder
          .addLibraryFiles(RecordTestUtils.getJdk15LibraryFiles(temp))
          .compile()
          .inspect(RecordTestUtils::assertRecordsAreRecords)
          .run(parameters.getRuntime(), MAIN_TYPE)
          .assertSuccessWithOutput(EXPECTED_RESULT);
      return;
    }
    builder.run(parameters.getRuntime(), MAIN_TYPE).assertSuccessWithOutput(EXPECTED_RESULT);
  }
}
