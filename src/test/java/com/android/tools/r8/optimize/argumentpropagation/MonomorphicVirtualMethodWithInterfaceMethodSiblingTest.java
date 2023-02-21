// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.BooleanBox;
import com.android.tools.r8.utils.codeinspector.HorizontallyMergedClassesInspector;
import com.android.tools.r8.utils.codeinspector.VerticallyMergedClassesInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MonomorphicVirtualMethodWithInterfaceMethodSiblingTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection parameters() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    BooleanBox inspected = new BooleanBox();
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addArgumentPropagatorCodeScannerResultInspector(
            inspector ->
                inspector
                    .assertHasPolymorphicMethodState(
                        Reference.methodFromMethod(I.class.getDeclaredMethod("m", int.class)))
                    .assertHasMonomorphicMethodState(
                        Reference.methodFromMethod(A.class.getDeclaredMethod("m", int.class)))
                    .assertHasBottomMethodState(
                        Reference.methodFromMethod(C.class.getDeclaredMethod("m", int.class)))
                    .apply(ignore -> inspected.set()))
        .addHorizontallyMergedClassesInspector(
            HorizontallyMergedClassesInspector::assertNoClassesMerged)
        .addVerticallyMergedClassesInspector(
            VerticallyMergedClassesInspector::assertNoClassesMerged)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A: 42", "A: 42");
    assertTrue(inspected.isTrue());
  }

  static class Main {

    public static void main(String[] args) {
      // Since A.m() does not override any methods (ignoring siblings) and do not have any overrides
      // this invoke leads to a monomorphic method state for A.m().
      A a = new A();
      a.m(42);

      // This invoke leads to a polymorphic method state for I.m(). When we propagate this
      // information to A.m() in the interface method propagation phase, we need to transform the
      // polymorphic method state into a monomorphic method state.
      I i = System.currentTimeMillis() >= 0 ? new B() : new C();
      i.m(42);
    }
  }

  interface I {

    void m(int x);
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  @NoVerticalClassMerging
  static class A {

    @NeverInline
    public void m(int x) {
      System.out.println("A: " + x);
    }
  }

  static class B extends A implements I {}

  @NoHorizontalClassMerging
  static class C implements I {

    @Override
    public void m(int x) {
      System.out.println("C: " + x);
    }
  }
}
