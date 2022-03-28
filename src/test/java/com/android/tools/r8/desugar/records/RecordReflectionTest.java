// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.records;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.utils.InternalOptions.TestingOptions;
import com.android.tools.r8.utils.StringUtils;
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
    return buildParameters(
        getTestParameters().withCfRuntimesStartingFromIncluding(CfVm.JDK17).build());
  }

  @Test
  public void testJvm() throws Exception {
    testForJvm()
        .addProgramClassFileData(PROGRAM_DATA)
        .run(parameters.getRuntime(), MAIN_TYPE)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testR8Cf() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClassFileData(PROGRAM_DATA)
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(MAIN_TYPE)
        .addKeepRules("-keepattributes *")
        .addKeepRules("-keep class * extends java.lang.Record { private final <fields>; }")
        .addLibraryFiles(RecordTestUtils.getJdk15LibraryFiles(temp))
        .addOptionsModification(TestingOptions::allowExperimentClassFileVersion)
        .compile()
        .inspect(RecordTestUtils::assertRecordsAreRecords)
        .enableJVMPreview()
        .run(parameters.getRuntime(), MAIN_TYPE)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }
}
