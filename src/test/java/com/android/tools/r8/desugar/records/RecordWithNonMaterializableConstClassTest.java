// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.records;

import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

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
  private static final String EXPECTED_RESULT_R8_ART14 =
      String.format(EXPECTED_RESULT_FORMAT, "a", "theClass");

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK17)
        .withDexRuntimes()
        .withAllApiLevelsAlsoForCf()
        .build();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClassFileData(PROGRAM_DATA)
        .addProgramClassFileData(EXTRA_DATA)
        .run(parameters.getRuntime(), MAIN_TYPE)
        .assertSuccessWithOutput(EXPECTED_RESULT_D8);
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters.getBackend())
        .addProgramClassFileData(PROGRAM_DATA)
        .addProgramClassFileData(EXTRA_DATA)
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), MAIN_TYPE)
        .assertSuccessWithOutput(EXPECTED_RESULT_D8);
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .addProgramClassFileData(PROGRAM_DATA)
        .addProgramClassFileData(EXTRA_DATA)
        .apply(this::configureR8)
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), MAIN_TYPE)
        .assertSuccessWithOutput(EXPECTED_RESULT_R8);
  }

  @Test
  public void testR8CfThenRecompile() throws Exception {
    parameters.assumeR8TestParameters();
    Path desugared =
        testForR8(Backend.CF)
            .addProgramClassFileData(PROGRAM_DATA)
            .addProgramClassFileData(EXTRA_DATA)
            .addLibraryFiles(RecordTestUtils.getJdk15LibraryFiles(temp))
            .apply(this::configureR8)
            .compile()
            .writeToZip();
    // TODO(b/288360309): Correctly deal with non-identity lenses in R8 record rewriting.
    assumeTrue(parameters.isDexRuntime());
    testForR8(parameters.getBackend())
        .addProgramFiles(desugared)
        .apply(this::configureR8)
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), MAIN_TYPE)
        .assertSuccessWithOutput(EXPECTED_RESULT_R8);
  }

  private void configureR8(R8FullTestBuilder testBuilder) {
    testBuilder
        .addKeepMainRule(MAIN_TYPE)
        .addKeepRules("-keep class " + PRIVATE_CLASS_NAME)
        .applyIf(
            parameters.isCfRuntime(),
            b -> b.addLibraryFiles(RecordTestUtils.getJdk15LibraryFiles(temp)));
  }
}
