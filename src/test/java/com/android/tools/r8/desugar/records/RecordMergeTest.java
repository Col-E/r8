// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.records;

import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfRuntime;
import com.android.tools.r8.utils.InternalOptions.TestingOptions;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RecordMergeTest extends TestBase {

  private static final String RECORD_NAME_1 = "RecordWithMembers";
  private static final byte[][] PROGRAM_DATA_1 = RecordTestUtils.getProgramData(RECORD_NAME_1);
  private static final String MAIN_TYPE_1 = RecordTestUtils.getMainType(RECORD_NAME_1);
  private static final String EXPECTED_RESULT_1 =
      StringUtils.lines(
          "BobX", "43", "BobX", "43", "FelixX", "-1", "FelixX", "-1", "print", "Bob43", "extra");

  private static final String RECORD_NAME_2 = "SimpleRecord";
  private static final byte[][] PROGRAM_DATA_2 = RecordTestUtils.getProgramData(RECORD_NAME_2);
  private static final String MAIN_TYPE_2 = RecordTestUtils.getMainType(RECORD_NAME_2);
  private static final String EXPECTED_RESULT_2 =
      StringUtils.lines("Jane Doe", "42", "Jane Doe", "42");

  private final TestParameters parameters;

  public RecordMergeTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Parameterized.Parameters(name = "{0}")
  public static List<Object[]> data() {
    // TODO(b/174431251): This should be replaced with .withCfRuntimes(start = jdk16).
    return buildParameters(
        getTestParameters()
            .withCustomRuntime(CfRuntime.getCheckedInJdk16())
            .withDexRuntimes()
            .withAllApiLevelsAlsoForCf()
            .build());
  }

  @Test
  public void testMergeDesugaredInputs() throws Exception {
    Path output1 =
        testForD8(parameters.getBackend())
            .addProgramClassFileData(PROGRAM_DATA_1)
            .setMinApi(parameters.getApiLevel())
            .addOptionsModification(TestingOptions::allowExperimentClassFileVersion)
            .compile()
            .writeToZip();
    Path output2 =
        testForD8(parameters.getBackend())
            .addProgramClassFileData(PROGRAM_DATA_2)
            .setMinApi(parameters.getApiLevel())
            .addOptionsModification(TestingOptions::allowExperimentClassFileVersion)
            .compile()
            .writeToZip();
    D8TestCompileResult result =
        testForD8(parameters.getBackend())
            .addProgramFiles(output1, output2)
            .setMinApi(parameters.getApiLevel())
            .addOptionsModification(TestingOptions::allowExperimentClassFileVersion)
            .compile();
    result.run(parameters.getRuntime(), MAIN_TYPE_1).assertSuccessWithOutput(EXPECTED_RESULT_1);
    result.run(parameters.getRuntime(), MAIN_TYPE_2).assertSuccessWithOutput(EXPECTED_RESULT_2);
  }

  @Test
  public void testMergeDesugaredAndNonDesugaredInputs() throws Exception {
    Path output1 =
        testForD8(parameters.getBackend())
            .addProgramClassFileData(PROGRAM_DATA_1)
            .setMinApi(parameters.getApiLevel())
            .addOptionsModification(TestingOptions::allowExperimentClassFileVersion)
            .compile()
            .writeToZip();
    D8TestCompileResult result =
        testForD8(parameters.getBackend())
            .addProgramFiles(output1)
            .addProgramClassFileData(PROGRAM_DATA_2)
            .setMinApi(parameters.getApiLevel())
            .addOptionsModification(TestingOptions::allowExperimentClassFileVersion)
            .compile();
    result.run(parameters.getRuntime(), MAIN_TYPE_1).assertSuccessWithOutput(EXPECTED_RESULT_1);
    result.run(parameters.getRuntime(), MAIN_TYPE_2).assertSuccessWithOutput(EXPECTED_RESULT_2);
  }
}
