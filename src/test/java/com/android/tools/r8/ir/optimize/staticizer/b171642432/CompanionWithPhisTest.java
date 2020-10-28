// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.staticizer.b171642432;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

// This is a reproduction of b/171642432.
@RunWith(Parameterized.class)
public class CompanionWithPhisTest extends TestBase {

  private final TestParameters parameters;
  private final String EXPECTED = "FooBarBaza.htm";

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public CompanionWithPhisTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(Main.class, CompanionUser.class)
        .addProgramClassesAndInnerClasses(ClassWithCompanion.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class, CompanionUser.class)
        .addProgramClassesAndInnerClasses(ClassWithCompanion.class)
        .setMinApi(parameters.getApiLevel())
        .enableNeverClassInliningAnnotations()
        .enableInliningAnnotations()
        .addKeepMainRule(Main.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  public static class Main {

    public static void main(String[] args) {
      System.out.println(
          new CompanionUser(args.length == 0 ? "FooBarBaz.htm" : args[0]).getItem(args.length).url);
    }
  }
}
