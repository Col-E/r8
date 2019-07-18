// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.deadcode;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RemoveDeadArray extends TestBase {

  public static class TestClassWithCatch {
    static {
      try {
        int[] foobar = new int[]{42, 42, 42, 42};
      } catch (Exception ex) {
        System.out.println("foobar");
      }
    }
  }

  public static class TestClass {
    private static int[] foobar = new int[]{42, 42, 42, 42};
    public static void main(java.lang.String[] args) {
      ShouldGoAway[] willBeRemoved = new ShouldGoAway[4];
      ShouldGoAway[] willAlsoBeRemoved = new ShouldGoAway[0];
      System.out.println("foobar");
    }

    public static class ShouldGoAway { }
  }

  private final TestParameters parameters;

  public RemoveDeadArray(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    // Todo(ricow): enable unused array removal for cf backend.
    return getTestParameters().withDexRuntimes().build();
  }


  @Test
  public void testDeadArraysRemoved() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class, TestClass.ShouldGoAway.class)
        .addKeepMainRule(TestClass.class)
        .compile()
        .inspect(
            inspector -> {
              MethodSubject clinit = inspector.clazz(TestClass.class).clinit();
              assertThat(clinit, not(isPresent()));

              MethodSubject main = inspector.clazz(TestClass.class).mainMethod();
              assertTrue(main.streamInstructions().noneMatch(InstructionSubject::isNewArray));
            })
        .run(parameters.getRuntime(), TestClass.class.getName());
 }

  @Test
  public void testNotRemoveStaticCatch() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClassWithCatch.class)
        .addKeepAllClassesRule()
        .compile()
        .inspect(
            inspector -> {
              MethodSubject clinit = inspector.clazz(TestClassWithCatch.class).clinit();
              assertThat(clinit, not(isPresent()));
            });
  }
}
