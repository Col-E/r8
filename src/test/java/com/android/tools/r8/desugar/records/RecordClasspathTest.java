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
import com.android.tools.r8.utils.BooleanUtils;
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
  private final boolean stripClasspath;

  public RecordClasspathTest(TestParameters parameters, boolean stripClasspath) {
    this.parameters = parameters;
    this.stripClasspath = stripClasspath;
  }

  @Parameterized.Parameters(name = "{0}, strip: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters()
            .withCfRuntimesStartingFromIncluding(CfVm.JDK17)
            .withDexRuntimes()
            .withAllApiLevelsAlsoForCf()
            .build(),
        BooleanUtils.values());
  }

  private byte[][] getClasspathData() {
    return stripClasspath ? stripFields(PROGRAM_DATA_1) : PROGRAM_DATA_1;
  }

  private byte[][] stripFields(byte[][] programData1) {
    byte[][] bytes = new byte[programData1.length][];
    for (int i = 0; i < programData1.length; i++) {
      bytes[i] =
          transformer(programData1[i], null).removeFields((a, b, c, d, e) -> true).transform();
    }
    return bytes;
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClasses(TestClass.class)
        .addClasspathClassFileData(getClasspathData())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters.getBackend())
        .addProgramClasses(TestClass.class)
        .addClasspathClassFileData(getClasspathData())
        .setMinApi(parameters)
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
        .addClasspathClassFileData(getClasspathData())
        .setMinApi(parameters)
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
    parameters.assumeR8TestParameters();
    R8FullTestBuilder builder =
        testForR8(parameters.getBackend())
            .addProgramClasses(TestClass.class)
            .addClasspathClassFileData(getClasspathData())
            .setMinApi(parameters)
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
