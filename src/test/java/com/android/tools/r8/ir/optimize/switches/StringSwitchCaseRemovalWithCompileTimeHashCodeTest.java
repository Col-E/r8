// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.switches;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject.JumboStringMode;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class StringSwitchCaseRemovalWithCompileTimeHashCodeTest extends TestBase {

  private final boolean enableStringSwitchConversion;
  private final TestParameters parameters;

  @Parameters(name = "{1}, enable string-switch conversion: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  public StringSwitchCaseRemovalWithCompileTimeHashCodeTest(
      boolean enableStringSwitchConversion, TestParameters parameters) {
    this.enableStringSwitchConversion = enableStringSwitchConversion;
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class)
        .addKeepMainRule(TestClass.class)
        .addOptionsModification(
            options -> {
              options.enableStringSwitchConversion = enableStringSwitchConversion;
              assertTrue(options.minimumStringSwitchSize >= 3);
              options.minimumStringSwitchSize = 2;
            })
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("FOO")
        .inspect(
            codeInspector -> {
              ClassSubject main = codeInspector.clazz(TestClass.class);
              assertThat(main, isPresent());
              MethodSubject mainMethod = main.mainMethod();
              assertThat(mainMethod, isPresent());
              assertEquals(0, countCall(mainMethod, "String", "hashCode"));
              // Only "FOO" left
              assertEquals(
                  1,
                  mainMethod
                      .streamInstructions()
                      .filter(i -> i.isConstString(JumboStringMode.ALLOW))
                      .count());
              // No branching points.
              assertEquals(
                  0,
                  mainMethod
                      .streamInstructions()
                      .filter(i -> i.isIf() || i.isIfEqz() || i.isIfNez())
                      .count());
            });
  }

  static class TestClass {
    private static String test(String input) {
      switch (input) {
        case "foo": return "FOO";
        case "bar": return "BAR";
        default: return "UNKNOWN";
      }
    }

    public static void main(String... args) {
      System.out.println(test("foo"));
    }
  }
}
