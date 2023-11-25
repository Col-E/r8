// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.records;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.GlobalSyntheticsConsumer;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.desugar.graph.DesugarGraphTestConsumer;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.synthesis.globals.GlobalSyntheticsTestingConsumer;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RecordInterfaceTest extends TestBase {

  private static final String RECORD_NAME = "RecordInterface";
  private static final byte[][] PROGRAM_DATA = RecordTestUtils.getProgramData(RECORD_NAME);
  private static final String MAIN_TYPE = RecordTestUtils.getMainType(RECORD_NAME);
  private static final String EXPECTED_RESULT = StringUtils.lines("Human");

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  private boolean isCfRuntimeWithNativeRecordSupport() {
    return parameters.isCfRuntime()
        && parameters.asCfRuntime().isNewerThanOrEqual(CfVm.JDK14)
        && parameters.getApiLevel().equals(AndroidApiLevel.B);
  }

  @Test
  public void testReference() throws Exception {
    assumeTrue(isCfRuntimeWithNativeRecordSupport());
    testForJvm(parameters)
        .addProgramClassFileData(PROGRAM_DATA)
        .run(parameters.getRuntime(), MAIN_TYPE)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters.getBackend())
        .addProgramClassFileData(PROGRAM_DATA)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), MAIN_TYPE)
        .applyIf(
            isRecordsDesugaredForD8(parameters)
                || runtimeWithRecordsSupport(parameters.getRuntime()),
            r -> r.assertSuccessWithOutput(EXPECTED_RESULT),
            r -> r.assertFailureWithErrorThatThrows(NoClassDefFoundError.class));
  }

  @Test
  public void testD8Intermediate() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    DesugarGraphTestConsumer consumer = new DesugarGraphTestConsumer();
    GlobalSyntheticsTestingConsumer globals = new GlobalSyntheticsTestingConsumer();
    Path path = compileIntermediate(globals);
    testForD8()
        .addProgramFiles(path)
        .applyIf(
            isRecordsDesugaredForD8(parameters),
            b ->
                b.getBuilder()
                    .addGlobalSyntheticsResourceProviders(globals.getIndexedModeProvider()),
            b -> assertFalse(globals.hasGlobals()))
        .apply(b -> b.getBuilder().setDesugarGraphConsumer(consumer))
        .setMinApi(parameters)
        .setIncludeClassesChecksum(true)
        .compile()
        .assertNoMessages()
        .run(parameters.getRuntime(), MAIN_TYPE)
        .assertSuccessWithOutput(EXPECTED_RESULT);
    assertNoEdgeToRecord(consumer);
  }

  @Test
  public void testD8IntermediateNoDesugaringInStep2() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    DesugarGraphTestConsumer consumer = new DesugarGraphTestConsumer();
    GlobalSyntheticsTestingConsumer globals = new GlobalSyntheticsTestingConsumer();
    Path path = compileIntermediate(globals);
    testForD8()
        .addProgramFiles(path)
        .applyIf(
            isRecordsDesugaredForD8(parameters),
            b ->
                b.getBuilder()
                    .addGlobalSyntheticsResourceProviders(globals.getIndexedModeProvider()),
            b -> assertFalse(globals.hasGlobals()))
        .apply(b -> b.getBuilder().setDesugarGraphConsumer(consumer))
        .setMinApi(parameters)
        .setIncludeClassesChecksum(true)
        // In Android Studio they disable desugaring at this point to improve build speed.
        .disableDesugaring()
        .compile()
        .assertNoMessages()
        .run(parameters.getRuntime(), MAIN_TYPE)
        .assertSuccessWithOutput(EXPECTED_RESULT);
    assertNoEdgeToRecord(consumer);
  }

  private Path compileIntermediate(GlobalSyntheticsConsumer globalSyntheticsConsumer)
      throws Exception {
    Origin fake = new PathOrigin(Paths.get("origin"));
    DesugarGraphTestConsumer consumer = new DesugarGraphTestConsumer();
    Path intermediate =
        testForD8(Backend.DEX)
            .apply(
                b -> {
                  // We avoid unknown origin here since they are not allowed when using a Graph
                  // consumer.
                  for (byte[] programDatum : PROGRAM_DATA) {
                    b.getBuilder().addClassProgramData(programDatum, fake);
                  }
                })
            .setMinApi(parameters)
            .setIntermediate(true)
            .setIncludeClassesChecksum(true)
            .apply(b -> b.getBuilder().setGlobalSyntheticsConsumer(globalSyntheticsConsumer))
            .apply(b -> b.getBuilder().setDesugarGraphConsumer(consumer))
            .compile()
            .assertNoMessages()
            .writeToZip();
    assertNoEdgeToRecord(consumer);
    return intermediate;
  }

  private void assertNoEdgeToRecord(DesugarGraphTestConsumer consumer) {
    assertEquals(0, consumer.totalEdgeCount());
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    assumeTrue(parameters.isDexRuntime() || isCfRuntimeWithNativeRecordSupport());
    R8FullTestBuilder builder =
        testForR8(parameters.getBackend())
            .addProgramClassFileData(PROGRAM_DATA)
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
    builder.run(parameters.getRuntime(), MAIN_TYPE).assertSuccessWithOutput(EXPECTED_RESULT);
  }
}
