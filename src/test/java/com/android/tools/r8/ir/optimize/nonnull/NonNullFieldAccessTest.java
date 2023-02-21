// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.nonnull;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NonNullFieldAccessTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public NonNullFieldAccessTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class)
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(this::verifyNonNullPropagation)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Live!");
  }

  private void verifyNonNullPropagation(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(TestClass.class);
    assertThat(classSubject, isPresent());
    assertThat(classSubject.uniqueMethodWithOriginalName("live"), isPresent());
    assertThat(classSubject.uniqueMethodWithOriginalName("dead"), not(isPresent()));
    assertThat(classSubject.uniqueMethodWithOriginalName("inlineable"), not(isPresent()));
  }

  static class TestClass {

    static Object f = new Object();

    public static void main(String[] args) {
      // In order to ensure that `f` will be followed by an assume-not-null instruction after
      // inlining, the non-null-tracker must insert an assume-not-null instruction.
      if (inlineable() != null) {
        live();
      } else {
        dead();
      }
    }

    private static Object inlineable() {
      return f;
    }

    @NeverInline
    private static void live() {
      System.out.println("Live!");
    }

    @NeverInline
    private static void dead() {
      System.out.println("Dead!");
    }
  }
}
