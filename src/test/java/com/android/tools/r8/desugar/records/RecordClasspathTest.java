// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.records;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.synthesis.globals.GlobalSyntheticsTestingConsumer;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.util.List;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * The test verifies records on classpath do not generate a record global synthetic on the program
 * if the program does not refer to record.
 */
@RunWith(Parameterized.class)
public class RecordClasspathTest extends TestBase {

  private static final String RECORD_NAME_1 = "RecordWithMembers";
  private static final byte[][] PROGRAM_DATA_1 = RecordTestUtils.getProgramData(RECORD_NAME_1);
  private static final String EXPECTED_RESULT = StringUtils.lines("Hello");

  private final TestParameters parameters;

  public RecordClasspathTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Parameterized.Parameters(name = "{0}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters()
            .withCfRuntimesStartingFromIncluding(CfVm.JDK17)
            .withDexRuntimes()
            .withAllApiLevelsAlsoForCf()
            .build());
  }

  @Test
  public void testD8AndJvm() throws Exception {
    if (parameters.isCfRuntime()) {
      testForJvm()
          .addProgramClasses(TestClass.class)
          .addClasspathClassFileData(PROGRAM_DATA_1)
          .run(parameters.getRuntime(), TestClass.class)
          .assertSuccessWithOutput(EXPECTED_RESULT);
    }
    testForD8(parameters.getBackend())
        .addProgramClasses(TestClass.class)
        .addClasspathClassFileData(PROGRAM_DATA_1)
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(this::assertNoRecord)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testD8DexPerFile() throws Exception {
    GlobalSyntheticsTestingConsumer globals = new GlobalSyntheticsTestingConsumer();
    Assume.assumeFalse(parameters.isCfRuntime());
    testForD8(parameters.getBackend())
        .addProgramClasses(TestClass.class)
        .addClasspathClassFileData(PROGRAM_DATA_1)
        .setMinApi(parameters.getApiLevel())
        .setIntermediate(true)
        .setOutputMode(OutputMode.DexFilePerClassFile)
        .apply(b -> b.getBuilder().setGlobalSyntheticsConsumer(globals))
        .compile()
        .inspect(this::assertNoRecord)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
    assertFalse(globals.hasGlobals());
  }

  @Test
  public void testR8() throws Exception {
    R8FullTestBuilder builder =
        testForR8(parameters.getBackend())
            .addProgramClasses(TestClass.class)
            .addClasspathClassFileData(PROGRAM_DATA_1)
            .setMinApi(parameters.getApiLevel())
            .addKeepMainRule(TestClass.class);
    if (parameters.isCfRuntime()) {
      builder
          .addLibraryFiles(RecordTestUtils.getJdk15LibraryFiles(temp))
          .compile()
          .inspect(this::assertNoRecord)
          .inspect(RecordTestUtils::assertRecordsAreRecords)
          .run(parameters.getRuntime(), TestClass.class)
          .assertSuccessWithOutput(EXPECTED_RESULT);
      return;
    }
    builder.run(parameters.getRuntime(), TestClass.class).assertSuccessWithOutput(EXPECTED_RESULT);
  }

  private void assertNoRecord(CodeInspector inspector) {
    // Verify that the record class was not added as part of the compilation.
    assertEquals(1, inspector.allClasses().size());
    assertTrue(inspector.clazz(TestClass.class).isPresent());
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println("Hello");
    }
  }
}
