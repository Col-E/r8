// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class StaticMethodWithConstantArgumentThroughCallChainTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection parameters() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject mainClassSubject = inspector.clazz(Main.class);
              assertThat(mainClassSubject, isPresent());

              // The test1(), test2(), and test3() methods have been optimized.
              for (int i = 1; i <= 3; i++) {
                MethodSubject testMethodSubject =
                    mainClassSubject.uniqueMethodWithOriginalName("test" + i);
                assertThat(testMethodSubject, isPresent());
                assertEquals(0, testMethodSubject.getProgramMethod().getParameters().size());
                assertTrue(
                    testMethodSubject.streamInstructions().noneMatch(InstructionSubject::isIf));
              }

              assertThat(mainClassSubject.uniqueMethodWithOriginalName("dead"), isAbsent());
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(
            "Hello from test1()",
            "Hello from test2()",
            "Hello from test3()",
            "Hello from test1()",
            "Hello from test2()",
            "Hello from test3()",
            "Hello from test1()",
            "Hello from test2()",
            "Hello from test3()");
  }

  static class Main {

    public static void main(String[] args) {
      test1(42);
      test1(42);
      test1(42);
    }

    @NeverInline
    static void test1(int x) {
      if (x == 42) {
        System.out.println("Hello from test1()");
      } else {
        dead();
      }
      test2(x);
    }

    @NeverInline
    static void test2(int x) {
      if (x == 42) {
        System.out.println("Hello from test2()");
      } else {
        dead();
      }
      test3(x);
    }

    @NeverInline
    static void test3(int x) {
      if (x == 42) {
        System.out.println("Hello from test3()");
      } else {
        dead();
      }
    }

    @NeverInline
    static void dead() {
      System.out.println("Unreachable");
    }
  }
}
