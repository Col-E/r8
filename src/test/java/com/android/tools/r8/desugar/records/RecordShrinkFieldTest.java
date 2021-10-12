// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.records;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.InternalOptions.TestingOptions;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RecordShrinkFieldTest extends TestBase {

  private static final String RECORD_NAME = "RecordShrinkField";
  private static final byte[][] PROGRAM_DATA = RecordTestUtils.getProgramData(RECORD_NAME);
  private static final String MAIN_TYPE = RecordTestUtils.getMainType(RECORD_NAME);
  private static final String EXPECTED_RESULT =
      StringUtils.lines("%s[name=Jane Doe, age=42, unused=-1]", "%s[name=Bob, age=42, unused=-1]");
  private static final String EXPECTED_RESULT_D8 =
      String.format(EXPECTED_RESULT, "Person", "Person");
  private static final String EXPECTED_RESULT_R8 = String.format(EXPECTED_RESULT, "a", "a");

  private final TestParameters parameters;

  public RecordShrinkFieldTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Parameterized.Parameters(name = "{0}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevelsAlsoForCf().build());
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters.getBackend())
        .addProgramClassFileData(PROGRAM_DATA)
        .setMinApi(parameters.getApiLevel())
        .addOptionsModification(TestingOptions::allowExperimentClassFileVersion)
        .compile()
        .run(parameters.getRuntime(), MAIN_TYPE)
        .assertSuccessWithOutput(EXPECTED_RESULT_D8);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClassFileData(PROGRAM_DATA)
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(MAIN_TYPE)
        .addOptionsModification(TestingOptions::allowExperimentClassFileVersion)
        .compile()
        .inspect(this::assertSingleField)
        .run(parameters.getRuntime(), MAIN_TYPE)
        .assertSuccessWithOutput(EXPECTED_RESULT_R8);
  }

  @Test
  public void testR8Compat() throws Exception {
    testForR8Compat(parameters.getBackend())
        .addProgramClassFileData(PROGRAM_DATA)
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(MAIN_TYPE)
        .addKeepRules(
            "-keepclassmembers,allowshrinking,allowoptimization class"
                + " records.RecordShrinkField$Person { <fields>; }")
        .addOptionsModification(TestingOptions::allowExperimentClassFileVersion)
        .compile()
        .inspect(this::assertSingleField)
        .run(parameters.getRuntime(), MAIN_TYPE)
        .assertSuccessWithOutput(EXPECTED_RESULT_R8);
  }

  @Test
  public void testR8CfThenDex() throws Exception {
    Path desugared =
        testForR8(Backend.CF)
            .addProgramClassFileData(PROGRAM_DATA)
            .setMinApi(parameters.getApiLevel())
            .addKeepMainRule(MAIN_TYPE)
            .addLibraryFiles(RecordTestUtils.getJdk15LibraryFiles(temp))
            .addOptionsModification(TestingOptions::allowExperimentClassFileVersion)
            .compile()
            .writeToZip();
    testForR8(parameters.getBackend())
        .addProgramFiles(desugared)
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(MAIN_TYPE)
        .addOptionsModification(TestingOptions::allowExperimentClassFileVersion)
        .compile()
        .inspect(this::assertSingleField)
        .run(parameters.getRuntime(), MAIN_TYPE)
        .assertSuccessWithOutput(EXPECTED_RESULT_R8);
  }

  private void assertSingleField(CodeInspector inspector) {
    ClassSubject recordClass = inspector.clazz("records.a");
    assertEquals(1, recordClass.allInstanceFields().size());
    assertEquals(
        "java.lang.String", recordClass.allInstanceFields().get(0).getField().type().toString());
  }
}
