// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.fields;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class FieldAnalysisTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public FieldAnalysisTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private static String EXPECTED_RESULT = StringUtils.lines("42", "7", "21", "42", "49", "28");

  @Test
  public void testD8AndJava() throws Exception {
    testForRuntime(parameters.getRuntime(), parameters.getApiLevel())
        .addInnerClasses(FieldAnalysisTest.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .setMinApi(parameters)
        .addInnerClasses(FieldAnalysisTest.class)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @NeverClassInline
  static class A {
    int f = 42;

    @NeverInline
    A() {}

    @NeverInline
    A(int f) {
      this.f = f;
    }
  }

  @NeverClassInline
  static class B extends A {
    @NeverInline
    B() {
      super();
    }

    @NeverInline
    B(int f) {
      this();
      this.f = f;
    }

    @NeverInline
    B(int unused, int f) {
      super(unused);
      this.f = f;
    }
  }

  @NeverClassInline
  static class C extends A {
    @NeverInline
    C() {
      super();
    }

    @NeverInline
    C(int f) {
      this();
      this.f = f + this.f;
    }

    @NeverInline
    C(int unused, int f) {
      super(unused);
      this.f = f + this.f;
    }
  }

  static class Main {
    public static void main(String[] args) {
      System.out.println(new B().f);
      System.out.println(new B(7).f);
      System.out.println(new B(7, 21).f);

      System.out.println(new C().f);
      System.out.println(new C(7).f);
      System.out.println(new C(7, 21).f);
    }
  }
}
