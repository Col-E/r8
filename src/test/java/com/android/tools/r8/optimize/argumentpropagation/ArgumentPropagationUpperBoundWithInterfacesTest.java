// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation;

import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.NoUnusedInterfaceRemoval;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.HorizontallyMergedClassesInspector;
import com.android.tools.r8.utils.codeinspector.VerticallyMergedClassesInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** Regression test for b/284188592. */
@RunWith(Parameterized.class)
public class ArgumentPropagationUpperBoundWithInterfacesTest extends TestBase {

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
        .addHorizontallyMergedClassesInspector(
            HorizontallyMergedClassesInspector::assertNoClassesMerged)
        .addVerticallyMergedClassesInspector(
            VerticallyMergedClassesInspector::assertNoClassesMerged)
        .enableNoHorizontalClassMergingAnnotations()
        .enableNoUnusedInterfaceRemovalAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("42");
  }

  static class Main {

    public static void main(String[] args) {
      // A value with upper bound class type B and interface I.
      B b = System.currentTimeMillis() > 0 ? new C() : new D();
      // A virtual invoke with arguments [42] and upper bound class type B and interface I.
      b.foo(42);
    }
  }

  @NoUnusedInterfaceRemoval
  interface I {}

  @NoVerticalClassMerging
  abstract static class A {

    abstract void foo(int i);
  }

  abstract static class B extends A {}

  @NoHorizontalClassMerging
  static class C extends B implements I {

    @Override
    void foo(int i) {
      System.out.println(i);
    }
  }

  @NoHorizontalClassMerging
  static class D extends B implements I {

    @Override
    void foo(int i) {
      System.out.println(i);
    }
  }
}
