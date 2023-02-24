// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.switches;


import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class StringSwitchWitNonConstIdTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public StringSwitchWitNonConstIdTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8()
        .addProgramClasses(Main.class)
        .release()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Foo", "Bar", "Baz", "Qux");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .release()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Foo", "Bar", "Baz", "Qux");
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
      int foo = one();
      switch (hashCode) {
        case 70822: // "Foo".hashCode()
          if (str.equals("Foo")) {
            id = foo;
          }
          break;
        case 66547: // "Bar".hashCode()
          if (str.equals("Bar")) {
            id = 2;
          }
          break;
        case 66555: // "Baz".hashCode()
          if (str.equals("Baz")) {
            id = 3;
          }
          break;
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

    @NeverInline
    static int one() {
      return 1;
    }
  }
}
