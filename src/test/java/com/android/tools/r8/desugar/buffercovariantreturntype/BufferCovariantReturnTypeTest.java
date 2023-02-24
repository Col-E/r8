// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.buffercovariantreturntype;

import static com.android.tools.r8.TestRuntime.CfVm.JDK11;
import static com.android.tools.r8.utils.FileUtils.JAR_EXTENSION;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class BufferCovariantReturnTypeTest extends TestBase {

  private static final Path JAR =
      Paths.get(ToolHelper.EXAMPLES_JAVA11_JAR_DIR)
          .resolve("buffercovariantreturntype" + JAR_EXTENSION);
  private static final String EXPECTED_RESULT_PER_BUFFER =
      StringUtils.lines("5", "2", "5", "0", "16", "0", "0", "2");
  private static final String EXPECTED_RESULT =
      new String(new char[14]).replace("\0", EXPECTED_RESULT_PER_BUFFER);

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimesStartingFromIncluding(JDK11)
        .withDexRuntimes()
        .withAllApiLevelsAlsoForCf()
        .build();
  }

  @Test
  public void testJVM() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramFiles(JAR)
        .run(parameters.getRuntime(), "buffercovariantreturntype.BufferCovariantReturnTypeMain")
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters.getBackend())
        .addProgramFiles(JAR)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), "buffercovariantreturntype.BufferCovariantReturnTypeMain")
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .addProgramFiles(JAR)
        .addKeepMainRule("buffercovariantreturntype.BufferCovariantReturnTypeMain")
        .setMinApi(parameters)
        .run(parameters.getRuntime(), "buffercovariantreturntype.BufferCovariantReturnTypeMain")
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }
}
