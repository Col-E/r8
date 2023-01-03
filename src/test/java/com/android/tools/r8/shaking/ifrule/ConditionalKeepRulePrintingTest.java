// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.ifrule;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ConditionalKeepRulePrintingTest extends TestBase {

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public ConditionalKeepRulePrintingTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void test() throws Exception {
    testForR8(Backend.CF)
        .addInnerClasses(ConditionalKeepRulePrintingTest.class)
        .addKeepClassRules(TestClass.class)
        .addKeepRules(
            "-if class "
                + typeName(TestClass.class)
                + " -keep class "
                + typeName(TestClass.class)
                + " { *** main(...); }")
        .compile()
        .apply(
            r -> {
              assertThat(r.getProguardConfiguration().toString(), containsString("*** main(...);"));
            });
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println("Hello, world");
    }
  }
}
