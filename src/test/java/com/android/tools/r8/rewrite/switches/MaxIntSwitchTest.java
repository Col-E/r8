// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.rewrite.switches;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MaxIntSwitchTest extends TestBase {

  private final TestParameters parameters;
  private final CompilationMode mode;

  @Parameterized.Parameters(name = "{0}, mode = {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(), CompilationMode.values());
  }

  // See b/177790310 for details.
  public MaxIntSwitchTest(TestParameters parameters, CompilationMode mode) {
    this.parameters = parameters;
    this.mode = mode;
  }

  private void checkResult(TestRunResult<?> result) {
    if (mode == CompilationMode.DEBUG
        && parameters.getDexRuntimeVersion().isOlderThanOrEqual(Version.V4_0_4)) {
      result.assertFailureWithErrorThatThrows(AssertionError.class);
    } else {
      result.assertSuccessWithOutputLines("good");
    }
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters.getBackend())
        .addInnerClasses(this.getClass())
        .setMinApi(parameters.getApiLevel())
        .setMode(mode)
        .run(parameters.getRuntime(), TestClass.class)
        .apply(this::checkResult);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(this.getClass())
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters.getApiLevel())
        .setMode(mode)
        .run(parameters.getRuntime(), TestClass.class)
        .apply(this::checkResult);
  }

  static class TestClass {
    public static void main(String[] args) {
      switch (0x7fffffff) {
        case 0x7fffffff:
          System.out.println("good");
          break;
        default:
          throw new AssertionError();
      }
    }
  }
}
