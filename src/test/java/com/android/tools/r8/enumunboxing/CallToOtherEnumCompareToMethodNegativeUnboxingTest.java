// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.enumunboxing;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.utils.codeinspector.EnumUnboxingInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CallToOtherEnumCompareToMethodNegativeUnboxingTest extends EnumUnboxingTestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), Main.class)
        .applyIf(
            parameters.isCfRuntime()
                || parameters.getDexRuntimeVersion().isNewerThanOrEqual(Version.V7_0_0),
            runResult -> runResult.assertFailureWithErrorThatThrows(ClassCastException.class),
            runResult -> runResult.assertSuccessWithOutputLines("0"));
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepClassRules(Foo.class)
        .addEnumUnboxingInspector(EnumUnboxingInspector::assertNoEnumsUnboxed)
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .applyIf(
            parameters.isCfRuntime()
                || parameters.getDexRuntimeVersion().isNewerThanOrEqual(Version.V7_0_0),
            runResult -> runResult.assertFailureWithErrorThatThrows(ClassCastException.class),
            runResult -> runResult.assertSuccessWithOutputLines("0"));
  }

  static class Main {

    public static void main(String[] args) {
      Enum<?> fooEnum = Foo.A;
      Enum<Bar> fooEnumInDisguise = (Enum<Bar>) fooEnum;
      System.out.println(fooEnumInDisguise.compareTo(Bar.B));
    }
  }

  enum Foo {
    A
  }

  enum Bar {
    B
  }
}
