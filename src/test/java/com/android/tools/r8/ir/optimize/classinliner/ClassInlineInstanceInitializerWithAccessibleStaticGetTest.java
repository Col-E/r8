// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ClassInlineInstanceInitializerWithAccessibleStaticGetTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ClassInlineInstanceInitializerWithAccessibleStaticGetTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(ClassInlineInstanceInitializerWithAccessibleStaticGetTest.class)
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  private void inspect(CodeInspector inspector) {
    assertThat(inspector.clazz(Candidate.class), not(isPresent()));
    assertThat(inspector.clazz(CandidateBase.class), not(isPresent()));
  }

  static class TestClass {

    public static void main(String[] args) {
      Environment.VALUE = true;
      System.out.print(new Candidate().get());
      Environment.VALUE = false;
      System.out.println(new Candidate().get());
    }
  }

  @NoVerticalClassMerging
  static class CandidateBase {

    final String f;

    CandidateBase() {
      if (Environment.VALUE) {
        f = "Hello";
      } else {
        f = " world!";
      }
    }
  }

  static class Candidate extends CandidateBase {

    @NeverInline
    String get() {
      return f;
    }
  }

  static class Environment {

    static boolean VALUE;
  }
}
