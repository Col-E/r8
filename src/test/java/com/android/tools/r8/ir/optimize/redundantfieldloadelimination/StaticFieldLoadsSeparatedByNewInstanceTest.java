// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.redundantfieldloadelimination;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

@RunWith(Parameterized.class)
public class StaticFieldLoadsSeparatedByNewInstanceTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(StaticFieldLoadsSeparatedByNewInstanceTest.class)
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines(getExpectedOutput());
  }

  private String getExpectedOutput() {
    if (parameters.isDexRuntime()
        && parameters.getRuntime().asDex().getVm().getVersion() == Version.V6_0_1) {
      return " world! world!";
    }
    return "Hello world!";
  }

  static class TestClass {

    static String greeting = " world!";

    public static void main(String[] args) {
      String worldGreeting = greeting;
      new HelloGreeter(greeting);
      System.out.println(worldGreeting);
    }
  }

  static class HelloGreeter {

    static {
      TestClass.greeting = "Hello";
    }

    HelloGreeter(String helloGreeting) {
      System.out.print(helloGreeting);
    }
  }
}
