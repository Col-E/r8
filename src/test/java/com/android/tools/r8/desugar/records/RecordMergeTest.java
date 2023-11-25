// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.records;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentIf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.D8TestBuilder;
import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.errors.DuplicateTypesDiagnostic;
import com.android.tools.r8.errors.MissingGlobalSyntheticsConsumerDiagnostic;
import com.android.tools.r8.synthesis.globals.GlobalSyntheticsTestingConsumer;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.nio.file.Path;
import org.junit.Assume;
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

  private final TestParameters parameters;

  public RecordMergeTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  @Test
  public void testNoGlobalSyntheticsConsumer() throws Exception {
    D8TestBuilder builder =
        testForD8(parameters.getBackend())
            .addProgramClassFileData(PROGRAM_DATA_1)
            .setMinApi(parameters)
            .setIntermediate(true);
    if (isRecordsDesugaredForD8(parameters)) {
      assertThrows(
          CompilationFailedException.class,
          () ->
              builder.compileWithExpectedDiagnostics(
                  diagnostics ->
                      diagnostics
                          .assertOnlyErrors()
                          .assertErrorsMatch(
                              diagnosticType(MissingGlobalSyntheticsConsumerDiagnostic.class))));
    } else {
      builder.compile();
    }
  }

  @Test
  public void testMergeDesugaredInputs() throws Exception {
    testMergeDesugaredInputsDexPerClass(false);
  }

  @Test
  public void testMergeDesugaredInputsDexPerClass() throws Exception {
    Assume.assumeTrue("CF is already run from the other test", parameters.isDexRuntime());
    testMergeDesugaredInputsDexPerClass(true);
  }

  private void testMergeDesugaredInputsDexPerClass(boolean filePerClass) throws Exception {
    GlobalSyntheticsTestingConsumer globals1 = new GlobalSyntheticsTestingConsumer();
    Path output1 =
        testForD8(parameters.getBackend())
            .addProgramClassFileData(PROGRAM_DATA_1)
            .setMinApi(parameters)
            .setIntermediate(true)
            .applyIf(
                filePerClass && !parameters.isCfRuntime(),
                b -> b.setOutputMode(OutputMode.DexFilePerClassFile))
            .apply(b -> b.getBuilder().setGlobalSyntheticsConsumer(globals1))
            .compile()
            .inspect(this::assertDoesNotHaveRecordTag)
            .writeToZip();

    GlobalSyntheticsTestingConsumer globals2 = new GlobalSyntheticsTestingConsumer();
    Path output2 =
        testForD8(parameters.getBackend())
            .addProgramClassFileData(PROGRAM_DATA_2)
            .setMinApi(parameters)
            .setIntermediate(true)
            .applyIf(
                filePerClass && !parameters.isCfRuntime(),
                b -> b.setOutputMode(OutputMode.DexFilePerClassFile))
            .apply(b -> b.getBuilder().setGlobalSyntheticsConsumer(globals2))
            .compile()
            .inspect(this::assertDoesNotHaveRecordTag)
            .writeToZip();

    assertTrue(isRecordsDesugaredForD8(parameters) ^ !globals1.hasGlobals());
    assertTrue(isRecordsDesugaredForD8(parameters) ^ !globals2.hasGlobals());

    D8TestCompileResult result =
        testForD8(parameters.getBackend())
            .addProgramFiles(output1, output2)
            .apply(
                b ->
                    b.getBuilder()
                        .addGlobalSyntheticsResourceProviders(globals1.getProviders())
                        .addGlobalSyntheticsResourceProviders(globals2.getProviders()))
            .setMinApi(parameters)
            .compile()
            .inspect(this::assertHasRecordTag);

    result
        .run(parameters.getRuntime(), MAIN_TYPE_1)
        .applyIf(
            isRecordsDesugaredForD8(parameters)
                || runtimeWithRecordsSupport(parameters.getRuntime()),
            r -> r.assertSuccessWithOutput(EXPECTED_RESULT_1),
            r -> r.assertFailureWithErrorThatThrows(NoClassDefFoundError.class));
    result
        .run(parameters.getRuntime(), MAIN_TYPE_2)
        .applyIf(
            isRecordsDesugaredForD8(parameters)
                || runtimeWithRecordsSupport(parameters.getRuntime()),
            r -> r.assertSuccessWithOutput(EXPECTED_RESULT_2),
            r -> r.assertFailureWithErrorThatThrows(NoClassDefFoundError.class));
  }

  @Test
  public void testMergeDesugaredAndNonDesugaredInputs() throws Exception {
    GlobalSyntheticsTestingConsumer globals1 = new GlobalSyntheticsTestingConsumer();
    Path output1 =
        testForD8(parameters.getBackend())
            .addProgramClassFileData(PROGRAM_DATA_1)
            .setMinApi(parameters)
            .setIntermediate(true)
            .apply(b -> b.getBuilder().setGlobalSyntheticsConsumer(globals1))
            .compile()
            .writeToZip();

    D8TestCompileResult result =
        testForD8(parameters.getBackend())
            .addProgramFiles(output1)
            .apply(
                b -> b.getBuilder().addGlobalSyntheticsResourceProviders(globals1.getProviders()))
            .addProgramClassFileData(PROGRAM_DATA_2)
            .setMinApi(parameters)
            .compile();
    result
        .run(parameters.getRuntime(), MAIN_TYPE_1)
        .applyIf(
            isRecordsDesugaredForD8(parameters)
                || runtimeWithRecordsSupport(parameters.getRuntime()),
            r -> r.assertSuccessWithOutput(EXPECTED_RESULT_1),
            r -> r.assertFailureWithErrorThatThrows(NoClassDefFoundError.class));
    result
        .run(parameters.getRuntime(), MAIN_TYPE_2)
        .applyIf(
            isRecordsDesugaredForD8(parameters)
                || runtimeWithRecordsSupport(parameters.getRuntime()),
            r -> r.assertSuccessWithOutput(EXPECTED_RESULT_2),
            r -> r.assertFailureWithErrorThatThrows(NoClassDefFoundError.class));
  }

  @Test
  public void testMergeNonIntermediates() throws Exception {
    Path output1 =
        testForD8(parameters.getBackend())
            .addProgramClassFileData(PROGRAM_DATA_1)
            .setMinApi(parameters)
            .compile()
            .inspect(this::assertHasRecordTag)
            .writeToZip();

    Path output2 =
        testForD8(parameters.getBackend())
            .addProgramClassFileData(PROGRAM_DATA_2)
            .setMinApi(parameters)
            .compile()
            .inspect(this::assertHasRecordTag)
            .writeToZip();

    if (!isRecordsDesugaredForD8(parameters)) {
      D8TestCompileResult result =
          testForD8(parameters.getBackend())
              .addProgramFiles(output1, output2)
              .setMinApi(parameters)
              .compile();
      result
          .run(parameters.getRuntime(), MAIN_TYPE_1)
          .applyIf(
              isRecordsDesugaredForD8(parameters)
                  || runtimeWithRecordsSupport(parameters.getRuntime()),
              r -> r.assertSuccessWithOutput(EXPECTED_RESULT_1),
              r -> r.assertFailureWithErrorThatThrows(NoClassDefFoundError.class));
      result
          .run(parameters.getRuntime(), MAIN_TYPE_2)
          .applyIf(
              isRecordsDesugaredForD8(parameters)
                  || runtimeWithRecordsSupport(parameters.getRuntime()),
              r -> r.assertSuccessWithOutput(EXPECTED_RESULT_2),
              r -> r.assertFailureWithErrorThatThrows(NoClassDefFoundError.class));
    } else {
      assertThrows(
          CompilationFailedException.class,
          () ->
              testForD8(parameters.getBackend())
                  .addProgramFiles(output1, output2)
                  .setMinApi(parameters)
                  .compileWithExpectedDiagnostics(
                      diagnostics ->
                          diagnostics
                              .assertOnlyErrors()
                              .assertErrorsMatch(diagnosticType(DuplicateTypesDiagnostic.class))));
    }
  }

  private void assertHasRecordTag(CodeInspector inspector) {
    // Note: this should be asserting on record tag.
    assertThat(
        inspector.clazz("java.lang.Record"), isPresentIf(isRecordsDesugaredForD8(parameters)));
  }

  private void assertDoesNotHaveRecordTag(CodeInspector inspector) {
    // Note: this should be asserting on record tag.
    assertThat(inspector.clazz("java.lang.Record"), isAbsent());
  }
}
