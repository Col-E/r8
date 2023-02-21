// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.string;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class StringBuilderWithLoopTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepClassAndMembersRules(Utils.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("1", "2");
  }

  static class Main {

    public static void main(String[] args) {
      StringBuilder sb1 = new StringBuilder();
      StringBuilder sb2 = new StringBuilder();
      int i = 1;
      String line;
      while ((line = Utils.readLine(i)) != null) {
        StringBuilder sb = i == 1 ? sb1 : sb2;
        sb.append(line);
        i++;
      }
      System.out.println(sb1.toString());
      System.out.println(sb2.toString());
    }
  }

  static class Utils {

    // @Keep
    static String readLine(int i) {
      if (i <= 2) {
        return Integer.toString(i);
      }
      return null;
    }
  }
}
