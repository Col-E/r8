// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class UsageInformationConsumerTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("Hello, world");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  public UsageInformationConsumerTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testConsumer() throws Exception {
    Box<String> usageData = new Box<>();
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class, UnusedClass.class)
        .addKeepClassAndMembersRules(TestClass.class)
        .apply(
            b -> b.getBuilder().setProguardUsageConsumer(ToolHelper.consumeString(usageData::set)))
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
    assertEquals(StringUtils.lines(UnusedClass.class.getTypeName()), usageData.get());
  }

  @Test
  public void testRule() throws Exception {
    testForR8(parameters.getBackend())
        .collectStdout()
        .addProgramClasses(TestClass.class, UnusedClass.class)
        .addKeepClassAndMembersRules(TestClass.class)
        .addKeepRules("-printusage")
        .setMinApi(parameters)
        .compile()
        .assertStdoutThatMatches(equalTo(StringUtils.lines(UnusedClass.class.getTypeName())))
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  static class UnusedClass {
    public static void foo() {
      System.out.println("FOO!");
    }
  }

  static class TestClass {
    public static void main(String[] args) {
      System.out.println("Hello, world");
    }
  }
}