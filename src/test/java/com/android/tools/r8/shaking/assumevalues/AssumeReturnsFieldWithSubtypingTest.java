// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.assumevalues;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AssumeReturnsFieldWithSubtypingTest extends TestBase {

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
        .addKeepRules(
            "-assumevalues class " + Main.class.getTypeName() + " {",
            "  java.lang.Object getGreeting() return " + Main.class.getTypeName() + ".greeting;",
            "}",
            // TODO(b/233828966): Maybe disallow shrinking of this in the first round of shaking.
            "-keepclassmembers,allowobfuscation class " + Main.class.getTypeName() + "{",
            "  java.lang.String greeting;",
            "}")
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  static class Main {

    static String greeting = System.currentTimeMillis() > 0 ? "Hello world!" : null;

    public static void main(String[] args) {
      System.out.println(getGreeting());
    }

    static Object getGreeting() {
      return "Unexpected";
    }
  }
}
