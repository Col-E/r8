// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.unusedarguments;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class UnusedArgumentRemovalWithOverridingTest extends TestBase {

  private final boolean minification;
  private final TestParameters parameters;

  @Parameters(name = "{1}, minification: {0}")
  public static List<Object[]> params() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  public UnusedArgumentRemovalWithOverridingTest(boolean minification, TestParameters parameters) {
    this.minification = minification;
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    String expectedOutput = StringUtils.lines("Hello world!", "Hello world!");
    testForR8(parameters.getBackend())
        .addInnerClasses(UnusedArgumentRemovalWithOverridingTest.class)
        .addKeepMainRule(TestClass.class)
        .enableNeverClassInliningAnnotations()
        .enableInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .minification(minification)
        .setMinApi(parameters)
        .compile()
        .inspect(this::verify)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(expectedOutput);
  }

  private void verify(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(B.class);
    assertThat(classSubject, isPresent());

    MethodSubject methodSubject = classSubject.uniqueMethodWithOriginalName("greeting");
    assertThat(methodSubject, isPresent());
    assertEquals(
        "java.lang.String", methodSubject.getMethod().getReference().proto.parameters.toString());
  }

  static class TestClass {

    public static void main(String[] args) {
      String greeting = System.currentTimeMillis() > 0 ? "Hello world!" : null;
      System.out.println(new A().greeting(greeting));
      System.out.println(new B().greeting(greeting));
    }
  }

  @NeverClassInline
  @NoVerticalClassMerging
  static class A {

    @NeverInline
    public String greeting(String used) {
      return System.currentTimeMillis() >= 0 ? used : null;
    }
  }

  @NeverClassInline
  static class B extends A {

    @NeverInline
    @Override
    public String greeting(String unused) {
      System.out.print("Hello ");
      return "world!";
    }
  }
}
