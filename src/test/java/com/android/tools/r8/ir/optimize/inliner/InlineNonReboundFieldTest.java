// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ir.optimize.inliner.testclasses.Greeting;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;

/** Regression test for b/128604123. */
public class InlineNonReboundFieldTest extends TestBase {

  @Test
  public void test() throws Exception {
    String expectedOutput = StringUtils.lines("Greeter: Hello world!");
    testForR8(Backend.DEX)
        .addProgramClasses(
            TestClass.class, Greeter.class, Greeting.class, Greeting.getGreetingBase())
        .addKeepMainRule(TestClass.class)
        .enableClassInliningAnnotations()
        .enableMergeAnnotations()
        .run(TestClass.class)
        .assertSuccessWithOutput(expectedOutput);
  }

  static class TestClass {

    public static void main(String[] args) {
      new Greeter("Hello world!").greet();
    }
  }

  @NeverClassInline
  static class Greeter extends Greeting {

    static String TAG = "Greeter";

    Greeter(String greeting) {
      this.greeting = greeting;
    }

    void greet() {
      System.out.println(TAG + ": " + greeting);
    }
  }
}
