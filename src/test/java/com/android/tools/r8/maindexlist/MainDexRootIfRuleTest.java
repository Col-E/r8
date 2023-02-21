// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.maindexlist;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.google.common.collect.ImmutableSet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MainDexRootIfRuleTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withDexRuntimes()
        .withApiLevelsEndingAtExcluding(apiLevelWithNativeMultiDexSupport())
        .build();
  }

  public MainDexRootIfRuleTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .addKeepClassRules(R.class)
        .addMainDexRules(
            "-keep class " + Main.class.getTypeName() + " { void main(java.lang.String[]); }")
        .addMainDexRules(
            "-if class " + MainDexRoot.class.getTypeName() + " { void methodWithSingleCaller(); }",
            "-keep class " + R.class.getTypeName())
        .collectMainDexClasses()
        .compile()
        .inspectMainDexClasses(
            mainDexClasses -> {
              // TODO(b/164019179): Fix if-rules
              // MainDexRoot will be traced during first round of main dex tracing and result in the
              // R class being kept due to the if-rule. When tracing in the second round, the
              // class MainDexRoot is gone and the method inlined, resulting in R not being added to
              // Main Dex.
              assertEquals(ImmutableSet.of(Main.class.getTypeName()), mainDexClasses);
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(R.class.getName());
  }

  public static class R {}

  public static class MainDexRoot {

    public static void methodWithSingleCaller() throws Exception {
      // Reflectively access class R
      String[] strings =
          new String[] {"com", "android", "tools", "r8", "maindexlist", "MainDexRootIfRuleTest$R"};
      Class<?> clazz = Class.forName(String.join(".", strings));
      System.out.println(clazz.getName());
    }
  }

  public static class Main {

    public static void main(String[] args) throws Exception {
      MainDexRoot.methodWithSingleCaller();
    }
  }
}
