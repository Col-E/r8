// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.string;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class UnusedStringBuilderFromCharSequenceWithAppendObjectTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public UnusedStringBuilderFromCharSequenceWithAppendObjectTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLinesIf(
            parameters.isCfRuntime()
                || parameters.getDexRuntimeVersion().isNewerThanOrEqual(Version.V7_0_0),
            "CustomCharSequence.length()",
            "CustomCharSequence.length()",
            "CustomCharSequence.length()",
            "CustomCharSequence.charAt(0)")
        .assertSuccessWithOutputLinesIf(
            parameters.isDexRuntime()
                && parameters.getDexRuntimeVersion().isOlderThan(Version.V7_0_0),
            "CustomCharSequence.toString()");
  }

  static class Main {

    public static void main(String[] args) {
      new StringBuilder(new CustomCharSequence());
    }
  }

  static class CustomCharSequence implements CharSequence {

    @Override
    public int length() {
      System.out.println("CustomCharSequence.length()");
      return 1;
    }

    @Override
    public char charAt(int i) {
      if (i != 0) {
        throw new RuntimeException();
      }
      System.out.println("CustomCharSequence.charAt(0)");
      return 'A';
    }

    @Override
    public CharSequence subSequence(int i, int i1) {
      throw new RuntimeException();
    }

    @Override
    public String toString() {
      System.out.println("CustomCharSequence.toString()");
      return "A";
    }
  }
}
