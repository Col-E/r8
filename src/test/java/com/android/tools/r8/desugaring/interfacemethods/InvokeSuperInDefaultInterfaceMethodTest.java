// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugaring.interfacemethods;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InvokeSuperInDefaultInterfaceMethodTest extends TestBase {

  private static final String EXPECTED_OUTPUT =
      StringUtils.lines("I.m()", "J.m()", "JImpl.m()", "I.m()", "KImpl.m()");

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addTestClasspath()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(InvokeSuperInDefaultInterfaceMethodTest.class)
        .addKeepMainRule(TestClass.class)
        .enableNeverClassInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  static class TestClass {

    public static void main(String[] args) {
      new JImpl().m();
      new KImpl().m();
    }
  }

  @NoVerticalClassMerging
  interface I {

    default void m() {
      System.out.println("I.m()");
    }
  }

  @NoVerticalClassMerging
  interface J extends I {

    @Override
    default void m() {
      I.super.m();
      System.out.println("J.m()");
    }
  }

  @NoVerticalClassMerging
  interface K extends I {

    // Intentionally does not override I.m().
  }

  @NeverClassInline
  static class JImpl implements J {

    @Override
    public void m() {
      J.super.m();
      System.out.println("JImpl.m()");
    }
  }

  @NeverClassInline
  static class KImpl implements K {

    @Override
    public void m() {
      K.super.m();
      System.out.println("KImpl.m()");
    }
  }
}
