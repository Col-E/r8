// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.NoUnusedInterfaceRemoval;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EquivalentConstructorsWithInterfaceValueToParentTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableNeverClassInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .enableNoUnusedInterfaceRemovalAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("K", "L");
  }

  static class Main {

    public static void main(String[] args) {
      new A(new KImpl());
      new B(new LImpl());
    }
  }

  static class Parent {
    Parent(J j) {
      j.m();
    }
  }

  @NeverClassInline
  static class A extends Parent {
    A(K k) {
      super(k);
    }
  }

  @NeverClassInline
  static class B extends Parent {
    B(L l) {
      super(l);
    }
  }

  @NoHorizontalClassMerging
  static class KImpl implements K {
    @Override
    public void m() {
      System.out.println("K");
    }
  }

  @NoHorizontalClassMerging
  static class LImpl implements L {
    @Override
    public void m() {
      System.out.println("L");
    }
  }

  @NoHorizontalClassMerging
  @NoUnusedInterfaceRemoval
  @NoVerticalClassMerging
  interface I {
    void m();
  }

  @NoHorizontalClassMerging
  @NoUnusedInterfaceRemoval
  @NoVerticalClassMerging
  interface J extends I {}

  @NoHorizontalClassMerging
  @NoUnusedInterfaceRemoval
  @NoVerticalClassMerging
  interface K extends J {}

  @NoHorizontalClassMerging
  @NoUnusedInterfaceRemoval
  @NoVerticalClassMerging
  interface L extends J {}
}
