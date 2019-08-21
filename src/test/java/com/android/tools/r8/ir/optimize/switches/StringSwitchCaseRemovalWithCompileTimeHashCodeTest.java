// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.switches;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject.JumboStringMode;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class StringSwitchCaseRemovalWithCompileTimeHashCodeTest extends TestBase {
  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().build();
  }

  public StringSwitchCaseRemovalWithCompileTimeHashCodeTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class)
        .addKeepMainRule(TestClass.class)
        .addOptionsModification(
            options -> {
              // TODO(b/135721688): Once a backend is in place for StringSwitch instructions,
              //  generalize switch case removal for IntSwitch instructions to Switch instructions.
              assert !options.enableStringSwitchConversion;
            })
        .setMinApi(parameters.getRuntime())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("FOO")
        .inspect(codeInspector -> {
          ClassSubject main = codeInspector.clazz(TestClass.class);
          assertThat(main, isPresent());
          MethodSubject mainMethod = main.mainMethod();
          assertThat(mainMethod, isPresent());
          assertEquals(0, countCall(mainMethod, "String", "hashCode"));
          // Only "FOO" left
          assertEquals(
              1,
              mainMethod.streamInstructions()
                  .filter(i -> i.isConstString(JumboStringMode.ALLOW)).count());
          // No branching points.
          assertEquals(
              0,
              mainMethod.streamInstructions()
                  .filter(i -> i.isIf() || i.isIfEqz() || i.isIfNez()).count());
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
