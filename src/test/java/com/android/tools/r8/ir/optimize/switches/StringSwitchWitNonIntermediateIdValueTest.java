// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.switches;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class StringSwitchWitNonIntermediateIdValueTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public StringSwitchWitNonIntermediateIdValueTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8()
        .addProgramClasses(Main.class)
        .addOptionsModification(options -> options.minimumStringSwitchSize = 4)
        .release()
        .setMinApi(parameters)
        .compile()
        .inspect(this::verifyRewrittenToIfs)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Foo", "Bar", "Baz", "Qux");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class)
        .addKeepMainRule(Main.class)
        .addOptionsModification(options -> options.minimumStringSwitchSize = 4)
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(this::verifyRewrittenToIfs)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Foo", "Bar", "Baz", "Qux");
  }

  private void verifyRewrittenToIfs(CodeInspector inspector) {
    MethodSubject testMethodSubject =
        inspector.clazz(Main.class).uniqueMethodWithOriginalName("test");
    assertThat(testMethodSubject, isPresent());
    assertTrue(testMethodSubject.streamInstructions().noneMatch(InstructionSubject::isSwitch));
    assertEquals(
        3, testMethodSubject.streamInstructions().filter(InstructionSubject::isIf).count());
  }

  static class Main {

    public static void main(String[] args) {
      test("Foo");
      test("Bar");
      test("Baz");
      test("Qux");
    }

    @NeverInline
    static void test(String str) {
      int hashCode = str.hashCode();
      int id = 0;
      outer:
      {
        int nonZeroId;
        switch (hashCode) {
          case 70822: // "Foo".hashCode()
            if (str.equals("Foo")) {
              nonZeroId = 1;
              break;
            }
            break outer;
          case 66547: // "Bar".hashCode()
            if (str.equals("Bar")) {
              nonZeroId = 2;
              break;
            }
            break outer;
          case 66555: // "Baz".hashCode()
            if (str.equals("Baz")) {
              nonZeroId = 3;
              break;
            }
            break outer;
          default:
            break outer;
        }
        id = nonZeroId;
      }
      switch (id) {
        case 1:
          System.out.println("Foo");
          break;
        case 2:
          System.out.println("Bar");
          break;
        case 3:
          System.out.println("Baz");
          break;
        default:
          System.out.println("Qux");
          break;
      }
    }
  }
}
