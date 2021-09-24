// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.records;


import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfRuntime;
import com.android.tools.r8.utils.InternalOptions.TestingOptions;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Path;
import java.util.List;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class SimpleRecordTest extends TestBase {

  private static final String RECORD_NAME = "SimpleRecord";
  private static final byte[][] PROGRAM_DATA = RecordTestUtils.getProgramData(RECORD_NAME);
  private static final String MAIN_TYPE = RecordTestUtils.getMainType(RECORD_NAME);
  private static final String EXPECTED_RESULT =
      StringUtils.lines("Jane Doe", "42", "Jane Doe", "42");

  private final TestParameters parameters;

  public SimpleRecordTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Parameterized.Parameters(name = "{0}")
  public static List<Object[]> data() {
    // TODO(b/174431251): This should be replaced with .withCfRuntimes(start = jdk17).
    return buildParameters(
        getTestParameters()
            .withCustomRuntime(CfRuntime.getCheckedInJdk17())
            .withDexRuntimes()
            .withAllApiLevelsAlsoForCf()
            .build());
  }

  @Test
  public void testD8AndJvm() throws Exception {
    if (parameters.isCfRuntime()) {
      testForJvm()
          .addProgramClassFileData(PROGRAM_DATA)
          .run(parameters.getRuntime(), MAIN_TYPE)
          .assertSuccessWithOutput(EXPECTED_RESULT);
    }
    testForD8(parameters.getBackend())
        .addProgramClassFileData(PROGRAM_DATA)
        .setMinApi(parameters.getApiLevel())
        .addOptionsModification(TestingOptions::allowExperimentClassFileVersion)
        .compile()
        .inspectWithOptions(
            RecordTestUtils::assertNoJavaLangRecord,
            options -> options.testing.disableRecordApplicationReaderMap = true)
        .run(parameters.getRuntime(), MAIN_TYPE)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testD8Intermediate() throws Exception {
    Assume.assumeTrue(parameters.isDexRuntime());
    Path path = compileIntermediate();
    testForD8()
        .addProgramFiles(path)
        .setMinApi(parameters.getApiLevel())
        .setIncludeClassesChecksum(true)
        .run(parameters.getRuntime(), MAIN_TYPE)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testD8IntermediateNoDesugaringInStep2() throws Exception {
    Assume.assumeTrue(parameters.isDexRuntime());
    Path path = compileIntermediate();
    // In Android Studio they disable desugaring at this point to improve build speed.
    testForD8()
        .addProgramFiles(path)
        .setMinApi(parameters.getApiLevel())
        .setIncludeClassesChecksum(true)
        .disableDesugaring()
        .run(parameters.getRuntime(), MAIN_TYPE)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  private Path compileIntermediate() throws Exception {
    return testForD8(Backend.DEX)
        .addProgramClassFileData(PROGRAM_DATA)
        .setMinApi(parameters.getApiLevel())
        .addOptionsModification(TestingOptions::allowExperimentClassFileVersion)
        .setIntermediate(true)
        .setIncludeClassesChecksum(true)
        .compile()
        .writeToZip();
  }

  @Test
  public void testR8() throws Exception {
    R8FullTestBuilder builder =
        testForR8(parameters.getBackend())
            .addProgramClassFileData(PROGRAM_DATA)
            .setMinApi(parameters.getApiLevel())
            .addKeepMainRule(MAIN_TYPE)
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
    builder
        .compile()
        .inspectWithOptions(
            RecordTestUtils::assertNoJavaLangRecord,
            options -> options.testing.disableRecordApplicationReaderMap = true)
        .run(parameters.getRuntime(), MAIN_TYPE)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testR8NoMinification() throws Exception {
    R8FullTestBuilder builder =
        testForR8(parameters.getBackend())
            .addProgramClassFileData(PROGRAM_DATA)
            .noMinification()
            .setMinApi(parameters.getApiLevel())
            .addKeepMainRule(MAIN_TYPE)
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
    builder
        .compile()
        .inspectWithOptions(
            RecordTestUtils::assertNoJavaLangRecord,
            options -> options.testing.disableRecordApplicationReaderMap = true)
        .run(parameters.getRuntime(), MAIN_TYPE)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }
}
