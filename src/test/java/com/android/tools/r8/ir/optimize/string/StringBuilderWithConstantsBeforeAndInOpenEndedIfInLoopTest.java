// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.string;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.google.common.collect.Sets;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** This is a regression test for b/237674850 */
@RunWith(Parameterized.class)
public class StringBuilderWithConstantsBeforeAndInOpenEndedIfInLoopTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public StringBuilderWithConstantsBeforeAndInOpenEndedIfInLoopTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class)
        .addKeepMainRule(TestClass.class)
        .addOptionsModification(
            options ->
                options.itemFactory.libraryMethodsReturningReceiver = Sets.newIdentityHashSet())
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class, "1", "3")
        .assertSuccessWithOutputLines("0.1.2.3.");
  }

  static class TestClass {

    public static void main(String[] args) {
      StringBuilder builder = new StringBuilder();
      builder.append("0.");
      for (int i = 0; i < args.length; ++i) {
        builder.append(args[i]);
        builder.append(".");
        if (i != args.length - 1) {
          builder.append("2.");
        }
      }
      System.out.println(builder.toString());
    }
  }
}
