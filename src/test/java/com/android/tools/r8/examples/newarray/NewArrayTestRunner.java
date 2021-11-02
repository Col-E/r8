// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.examples.newarray;

import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class NewArrayTestRunner extends TestBase {

  static final Class<?> CLASS = NewArray.class;

  private final TestParameters parameters;
  private final CompilationMode mode;

  private static final String EXPECTED =
      "0\n0\n1\n0\n1\n2\n0\n1\n2\n3\n0\n1\n2\n3\n4\n0\n1\n2\n3\n4\n5\n0\n1\n2\n3\n4\n5\n6\n0\n1\n"
          + "2\n3\n4\n5\n6\n7\n0\n1\n0\n3\n4\n0\n6\n7\n0\n0\n1\n0\n1\n2\n0\n1\n2\n3\n0\n1\n2\n3\n"
          + "4\n0\n1\n2\n3\n4\n5\n0\n1\n2\n3\n4\n5\n0\n1\n0\n4\n0\n0\n0\n1\n0\n1\n2\n0\n1\n2\n3\n"
          + "0\n1\n2\n3\n4\n0\n1\n2\n3\n4\n5\n0\n1\n2\n3\n4\n5\n6\n0\n1\n2\n0\n3\n4\n5\n6\n6\n6\n"
          + "6\n6\n6\n1\n1\n1\n1\n1\n1\n8\n8\n8\n8\n2\n4\n6\n8\n10\n12\n14\n16\nfalse\n0\n\0\n0\n"
          + "0\n0.0\n0.0\nnull\n";

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
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testD8() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForD8()
        .addProgramClassesAndInnerClasses(CLASS)
        .setMinApi(parameters.getApiLevel())
        .setMode(mode)
        .run(parameters.getRuntime(), CLASS)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClassesAndInnerClasses(CLASS)
        .addKeepMainRule(CLASS)
        .setMinApi(parameters.getApiLevel())
        .setMode(mode)
        .run(parameters.getRuntime(), CLASS)
        .assertSuccessWithOutput(EXPECTED);
  }
}
