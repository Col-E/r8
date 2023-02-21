// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverPropagateValue;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ClassInlineInstanceInitializerWithIndirectEscapingReceiverTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ClassInlineInstanceInitializerWithIndirectEscapingReceiverTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(ClassInlineInstanceInitializerWithIndirectEscapingReceiverTest.class)
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .enableMemberValuePropagationAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  private void inspect(CodeInspector inspector) {
    assertThat(inspector.clazz(Candidate.class), isPresent());
    assertThat(inspector.clazz(CandidateBase.class), isPresent());
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(new Candidate().get());
    }
  }

  @NoVerticalClassMerging
  static class CandidateBase {

    CandidateBase() {
      Escape.escape(getReceiver());
    }

    @NeverInline
    Object getReceiver() {
      return System.currentTimeMillis() >= 0 ? this : null;
    }
  }

  static class Candidate extends CandidateBase {

    @NeverInline
    @NeverPropagateValue
    String get() {
      return "Hello world!";
    }
  }

  static class Escape {

    @NeverInline
    static void escape(Object o) {
      if (System.currentTimeMillis() < 0) {
        System.out.println(o);
      }
    }
  }
}
