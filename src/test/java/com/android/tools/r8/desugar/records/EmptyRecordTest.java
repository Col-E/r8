// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.records;

import static org.junit.Assume.assumeFalse;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EmptyRecordTest extends TestBase {

  private static final String RECORD_NAME = "EmptyRecord";
  private static final byte[][] PROGRAM_DATA = RecordTestUtils.getProgramData(RECORD_NAME);
  private static final String MAIN_TYPE = RecordTestUtils.getMainType(RECORD_NAME);
  private static final String EXPECTED_RESULT_D8 = StringUtils.lines("Empty[]");
  private static final String EXPECTED_RESULT_R8_MINIFICATION = StringUtils.lines("a[]");
  private static final String EXPECTED_RESULT_R8_NO_MINIFICATION =
      StringUtils.lines("EmptyRecord$Empty[]");

  @Parameter(0)
  public boolean enableMinification;

  @Parameter(1)
  public TestParameters parameters;

  @Parameters(name = "{1}, minification: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(),
        getTestParameters()
            .withCfRuntimesStartingFromIncluding(CfVm.JDK17)
            .withDexRuntimes()
            .withAllApiLevelsAlsoForCf()
            .build());
  }

  @Test
  public void testD8AndJvm() throws Exception {
    assumeFalse("Only applicable for R8", enableMinification);
    if (parameters.isCfRuntime()) {
      testForJvm(parameters)
          .addProgramClassFileData(PROGRAM_DATA)
          .run(parameters.getRuntime(), MAIN_TYPE)
          .assertSuccessWithOutput(EXPECTED_RESULT_D8);
    }
    testForD8(parameters.getBackend())
        .addProgramClassFileData(PROGRAM_DATA)
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), MAIN_TYPE)
        .assertSuccessWithOutput(EXPECTED_RESULT_D8);
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .addProgramClassFileData(PROGRAM_DATA)
        .addKeepMainRule(MAIN_TYPE)
        .applyIf(
            parameters.isCfRuntime(),
            testBuilder -> testBuilder.addLibraryFiles(RecordTestUtils.getJdk15LibraryFiles(temp)))
        .minification(enableMinification)
        .setMinApi(parameters)
        .compile()
        .applyIf(
            parameters.isCfRuntime(),
            compileResult -> compileResult.inspect(RecordTestUtils::assertRecordsAreRecords))
        .run(parameters.getRuntime(), MAIN_TYPE)
        .assertSuccessWithOutput(
            enableMinification
                ? EXPECTED_RESULT_R8_MINIFICATION
                : EXPECTED_RESULT_R8_NO_MINIFICATION);
  }
}
