// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.examples.newarray;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NewArrayTestRunner extends TestBase {

  private static final Class<?> CLASS = NewArray.class;

  @Parameter(0)
  public boolean enableMultiANewArrayDesugaringForClassFiles;

  @Parameter(1)
  public TestParameters parameters;

  @Parameter(2)
  public CompilationMode mode;

  private static final List<String> EXPECTED =
      ImmutableList.of(
          "[]",
          "[0]",
          "[0,1]",
          "[0,1,2]",
          "[0,1,2,3]",
          "[0,1,2,3,4]",
          "[0,1,2,3,4,5]",
          "[0,1,2,3,4,5,6]",
          "[0,1,2,3,4,5,6,7]",
          "[0,1,2,3,4,5,6,7]",
          "[]",
          "[0]",
          "[0,1]",
          "[0,1,2]",
          "[0,1,2,3]",
          "[0,1,2,3,4]",
          "[0,1,2,3,4,5]",
          "[0,1,2,3,4,5,6,7,8,9,10]",
          "[]",
          "[0]",
          "[0,1]",
          "[0,1,2]",
          "[0,1,2,3]",
          "[0,1,2,3,4]",
          "[0,1,2,3,4,5]",
          "[0,1,2,3,4,5,6]",
          "[0,1,2,3,4,5,6,7]",
          "6,6,6,6,6",
          "1,1,1,1,1,1",
          "8,8,8,8",
          "2,4,6,8,10,12,14,16,false,0,0,0,0,0.0,0.0,null");

  @Parameters(name = "{1}, {2}, force desugaring: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(),
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        CompilationMode.values());
  }

  @Test
  public void runReference() throws Exception {
    parameters.assumeJvmTestParameters();
    assumeFalse(enableMultiANewArrayDesugaringForClassFiles);
    assumeTrue(mode == CompilationMode.DEBUG);
    testForJvm(parameters)
        .addProgramClassesAndInnerClasses(CLASS)
        .run(parameters.getRuntime(), CLASS)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    assumeFalse(enableMultiANewArrayDesugaringForClassFiles);
    testForD8()
        .addProgramClassesAndInnerClasses(CLASS)
        .setMinApi(parameters)
        .setMode(mode)
        .run(parameters.getRuntime(), CLASS)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    assumeTrue(parameters.isCfRuntime() || !enableMultiANewArrayDesugaringForClassFiles);
    testForR8(parameters.getBackend())
        .addProgramClassesAndInnerClasses(CLASS)
        .addKeepMainRule(CLASS)
        .addOptionsModification(
            options ->
                options.testing.enableMultiANewArrayDesugaringForClassFiles =
                    enableMultiANewArrayDesugaringForClassFiles)
        .setMinApi(parameters)
        .setMode(mode)
        .run(parameters.getRuntime(), CLASS)
        .assertSuccessWithOutputLines(EXPECTED);
  }
}
