// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.startup;

import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class StartupInstrumentationTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testD8() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForD8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addOptionsModification(
            options -> options.getStartupOptions().setEnableStartupInstrumentation())
        .setMinApi(parameters.getApiLevel())
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(getExpectedOutput());
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepClassAndMembersRules(Main.class)
        .addOptionsModification(
            options -> options.getStartupOptions().setEnableStartupInstrumentation())
        .enableInliningAnnotations()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(getExpectedOutput());
  }

  private static List<String> getExpectedOutput() {
    return ImmutableList.of(descriptor(Main.class), descriptor(AStartupClass.class), "foo");
  }

  static class Main {

    public static void main(String[] args) {
      AStartupClass.foo();
    }

    // @Keep
    public void onClick() {
      NonStartupClass.bar();
    }
  }

  static class AStartupClass {

    @NeverInline
    static void foo() {
      System.out.println("foo");
    }
  }

  static class NonStartupClass {

    @NeverInline
    static void bar() {
      System.out.println("bar");
    }
  }
}
