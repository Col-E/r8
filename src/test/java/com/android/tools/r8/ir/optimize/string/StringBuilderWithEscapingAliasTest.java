// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.string;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.Sets;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class StringBuilderWithEscapingAliasTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public StringBuilderWithEscapingAliasTest(TestParameters parameters) {
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
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(StringUtils.times(StringUtils.lines("Hello world!"), 2));
  }

  static class TestClass {

    public static void main(String[] args) {
      StringBuilder builder = new StringBuilder();
      StringBuilder alias = builder.append("Hello");
      builder.append(" world!");
      System.out.println(builder.toString());
      System.out.println(alias.toString());
    }
  }
}
