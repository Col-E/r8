// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.records;

import static com.android.tools.r8.desugar.records.RecordTestUtils.RECORD_KEEP_RULE;

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
public class RecordReflectionTest extends TestBase {

  private static final String RECORD_NAME = "RecordReflection";
  private static final byte[][] PROGRAM_DATA = RecordTestUtils.getProgramData(RECORD_NAME);
  private static final String MAIN_TYPE = RecordTestUtils.getMainType(RECORD_NAME);
  private static final String EXPECTED_RESULT =
      StringUtils.lines(
          "true",
          "[]",
          "true",
          "[java.lang.String name, int age]",
          "true",
          "[java.lang.CharSequence name, int age]",
          "[S]",
          "false",
          "null");

  private final TestParameters parameters;

  public RecordReflectionTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Parameterized.Parameters(name = "{0}")
  public static List<Object[]> data() {
    // TODO(b/174431251): This should be replaced with .withCfRuntimes(start = jdk16).
    return buildParameters(
        getTestParameters().withCustomRuntime(CfRuntime.getCheckedInJdk16()).build());
  }

  @Test
  public void testJvm() throws Exception {
    testForJvm()
        .addProgramClassFileData(PROGRAM_DATA)
        .enablePreview()
        .run(parameters.getRuntime(), MAIN_TYPE)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testR8Cf() throws Exception {
    Path output =
        testForR8(parameters.getBackend())
            .addProgramClassFileData(PROGRAM_DATA)
            .setMinApi(parameters.getApiLevel())
            .addKeepRules(RECORD_KEEP_RULE)
            .addKeepMainRule(MAIN_TYPE)
            .addLibraryFiles(RecordTestUtils.getJdk15LibraryFiles(temp))
            .addOptionsModification(TestingOptions::allowExperimentClassFileVersion)
            .compile()
            .writeToZip();
    RecordTestUtils.assertRecordsAreRecords(output);
    testForJvm()
        .addRunClasspathFiles(output)
        .enablePreview()
        .run(parameters.getRuntime(), MAIN_TYPE)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }
}
