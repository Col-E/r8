// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.records;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.GlobalSyntheticsConsumer;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.synthesis.globals.GlobalSyntheticsTestingConsumer;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SimpleRecordTest extends TestBase {

  private static final String RECORD_NAME = "SimpleRecord";
  private static final byte[][] PROGRAM_DATA = RecordTestUtils.getProgramData(RECORD_NAME);
  private static final String MAIN_TYPE = RecordTestUtils.getMainType(RECORD_NAME);
  private static final String EXPECTED_RESULT =
      StringUtils.lines(
          "Jane Doe",
          "42",
          "Jane Doe",
          "42",
          "true",
          "true",
          "true",
          "false",
          "false",
          "false",
          "false");

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean forceInvokeRangeForInvokeCustom;

  @Parameters(name = "{0}, forceInvokeRangeForInvokeCustom: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build(),
        BooleanUtils.values());
  }

  private boolean isCfRuntimeWithNativeRecordSupport() {
    return parameters.isCfRuntime()
        && parameters.asCfRuntime().isNewerThanOrEqual(CfVm.JDK14)
        && parameters.getApiLevel().equals(AndroidApiLevel.B);
  }

  @Test
  public void testReference() throws Exception {
    assumeTrue(isCfRuntimeWithNativeRecordSupport());
    assumeFalse(forceInvokeRangeForInvokeCustom);
    testForJvm(parameters)
        .addProgramClassFileData(PROGRAM_DATA)
        .run(parameters.getRuntime(), MAIN_TYPE)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testD8() throws Exception {
    assumeFalse(forceInvokeRangeForInvokeCustom);
    testForD8(parameters.getBackend())
        .addProgramClassFileData(PROGRAM_DATA)
        .setMinApi(parameters)
        .compile()
        .inspectWithOptions(
            RecordTestUtils::assertNoJavaLangRecord,
            options -> options.testing.disableRecordApplicationReaderMap = true)
        .run(parameters.getRuntime(), MAIN_TYPE)
        .applyIf(
            isRecordsDesugaredForD8(parameters)
                || runtimeWithRecordsSupport(parameters.getRuntime()),
            r -> r.assertSuccessWithOutput(EXPECTED_RESULT),
            r -> r.assertFailureWithErrorThatThrows(NoClassDefFoundError.class));
    ;
  }

  @Test
  public void testD8Intermediate() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    assumeFalse(forceInvokeRangeForInvokeCustom);
    GlobalSyntheticsTestingConsumer globals = new GlobalSyntheticsTestingConsumer();
    Path path = compileIntermediate(globals);
    testForD8()
        .addProgramFiles(path)
        .apply(
            b ->
                b.getBuilder()
                    .addGlobalSyntheticsResourceProviders(globals.getIndexedModeProvider()))
        .setMinApi(parameters)
        .setIncludeClassesChecksum(true)
        .run(parameters.getRuntime(), MAIN_TYPE)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testD8IntermediateNoDesugaringInStep2() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    assumeFalse(forceInvokeRangeForInvokeCustom);
    GlobalSyntheticsTestingConsumer globals = new GlobalSyntheticsTestingConsumer();
    Path path = compileIntermediate(globals);
    // In Android Studio they disable desugaring at this point to improve build speed.
    testForD8()
        .addProgramFiles(path)
        .apply(
            b ->
                b.getBuilder()
                    .addGlobalSyntheticsResourceProviders(globals.getIndexedModeProvider()))
        .setMinApi(parameters)
        .setIncludeClassesChecksum(true)
        .disableDesugaring()
        .run(parameters.getRuntime(), MAIN_TYPE)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  private Path compileIntermediate(GlobalSyntheticsConsumer globalSyntheticsConsumer)
      throws Exception {
    return testForD8(Backend.DEX)
        .addProgramClassFileData(PROGRAM_DATA)
        .setMinApi(parameters)
        .setIntermediate(true)
        .setIncludeClassesChecksum(true)
        .apply(b -> b.getBuilder().setGlobalSyntheticsConsumer(globalSyntheticsConsumer))
        .compile()
        .writeToZip();
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    assumeTrue(parameters.isDexRuntime() || isCfRuntimeWithNativeRecordSupport());
    assumeTrue(forceInvokeRangeForInvokeCustom || !parameters.isDexRuntime());
    R8FullTestBuilder builder =
        testForR8(parameters.getBackend())
            .addOptionsModification(
                opptions ->
                    opptions.testing.forceInvokeRangeForInvokeCustom =
                        forceInvokeRangeForInvokeCustom)
            .addProgramClassFileData(PROGRAM_DATA)
            .setMinApi(parameters)
            .addKeepMainRule(MAIN_TYPE);
    if (parameters.isCfRuntime()) {
      builder
          .addLibraryFiles(RecordTestUtils.getJdk15LibraryFiles(temp))
          .compile()
          .inspect(RecordTestUtils::assertRecordsAreRecords)
          .inspect(
              inspector -> {
                inspector.clazz("records.SimpleRecord$Person").isRenamed();
              })
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
    parameters.assumeR8TestParameters();
    assumeTrue(parameters.isDexRuntime() || isCfRuntimeWithNativeRecordSupport());
    assumeTrue(forceInvokeRangeForInvokeCustom || !parameters.isDexRuntime());
    R8FullTestBuilder builder =
        testForR8(parameters.getBackend())
            .addProgramClassFileData(PROGRAM_DATA)
            .addDontObfuscate()
            .setMinApi(parameters)
            .addKeepMainRule(MAIN_TYPE);
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
