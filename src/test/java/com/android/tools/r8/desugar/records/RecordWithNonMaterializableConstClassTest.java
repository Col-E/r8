// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.records;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RecordWithNonMaterializableConstClassTest extends TestBase {

  private static final String RECORD_NAME = "RecordWithConstClass";
  private static final String PRIVATE_CLASS_NAME =
      "records.differentpackage.PrivateConstClass$PrivateClass";
  private static final byte[][] PROGRAM_DATA = RecordTestUtils.getProgramData(RECORD_NAME);
  private static final byte[][] EXTRA_DATA =
      RecordTestUtils.getProgramData("differentpackage/PrivateConstClass");
  private static final String MAIN_TYPE = RecordTestUtils.getMainType(RECORD_NAME);
  private static final String EXPECTED_RESULT_FORMAT =
      StringUtils.lines("%s[%s=class " + PRIVATE_CLASS_NAME + "]");
  private static final String EXPECTED_RESULT_D8 =
      String.format(EXPECTED_RESULT_FORMAT, "MyRecordWithConstClass", "theClass");
  private static final String EXPECTED_RESULT_R8 = String.format(EXPECTED_RESULT_FORMAT, "a", "a");

  private final TestParameters parameters;

  public RecordWithNonMaterializableConstClassTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Parameterized.Parameters(name = "{0}")
  public static List<Object[]> data() {
    // TODO(b/174431251): This should be replaced with .withCfRuntimes(start = jdk17).
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevelsAlsoForCf().build());
  }

  @Test
  public void testD8AndJvm() throws Exception {
    if (parameters.isCfRuntime()) {
      testForJvm()
          .addProgramClassFileData(PROGRAM_DATA)
          .addProgramClassFileData(EXTRA_DATA)
          .run(parameters.getRuntime(), MAIN_TYPE)
          .assertSuccessWithOutput(EXPECTED_RESULT_D8);
    }
    testForD8(parameters.getBackend())
        .addProgramClassFileData(PROGRAM_DATA)
        .addProgramClassFileData(EXTRA_DATA)
        .setMinApi(parameters.getApiLevel())
        .compile()
        .run(parameters.getRuntime(), MAIN_TYPE)
        .assertSuccessWithOutput(EXPECTED_RESULT_D8);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClassFileData(PROGRAM_DATA)
        .addProgramClassFileData(EXTRA_DATA)
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(MAIN_TYPE)
        .addKeepRules("-keep class " + PRIVATE_CLASS_NAME)
        .compile()
        .run(parameters.getRuntime(), MAIN_TYPE)
        .assertSuccessWithOutput(EXPECTED_RESULT_R8);
  }

  @Test
  public void testR8CfThenDex() throws Exception {
    Path desugared =
        testForR8(Backend.CF)
            .addProgramClassFileData(PROGRAM_DATA)
            .addProgramClassFileData(EXTRA_DATA)
            .setMinApi(parameters.getApiLevel())
            .addKeepMainRule(MAIN_TYPE)
            .addKeepRules("-keep class " + PRIVATE_CLASS_NAME)
            .addLibraryFiles(RecordTestUtils.getJdk15LibraryFiles(temp))
            .compile()
            .writeToZip();
    testForR8(parameters.getBackend())
        .addProgramFiles(desugared)
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(MAIN_TYPE)
        .addKeepRules("-keep class " + PRIVATE_CLASS_NAME)
        .compile()
        .run(parameters.getRuntime(), MAIN_TYPE)
        .assertSuccessWithOutput(EXPECTED_RESULT_R8);
  }
}
