// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.examples.newarray;

import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class NewArrayTestRunner extends TestBase {

  static final Class<?> CLASS = NewArray.class;

  private final TestParameters parameters;
  private final CompilationMode mode;

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

  @Parameterized.Parameters(name = "{0}, {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimes().withAllApiLevels().build(), CompilationMode.values());
  }

  public NewArrayTestRunner(TestParameters parameters, CompilationMode mode) {
    this.parameters = parameters;
    this.mode = mode;
  }

  @Test
  public void runReference() throws Exception {
    assumeTrue(parameters.isCfRuntime() && mode == CompilationMode.DEBUG);
    testForJvm(getStaticTemp())
        .addProgramClassesAndInnerClasses(CLASS)
        .run(parameters.getRuntime(), CLASS)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testD8() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForD8()
        .addProgramClassesAndInnerClasses(CLASS)
        .setMinApi(parameters.getApiLevel())
        .setMode(mode)
        .run(parameters.getRuntime(), CLASS)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClassesAndInnerClasses(CLASS)
        .addKeepMainRule(CLASS)
        .setMinApi(parameters.getApiLevel())
        .setMode(mode)
        .run(parameters.getRuntime(), CLASS)
        .assertSuccessWithOutputLines(EXPECTED);
  }
}
