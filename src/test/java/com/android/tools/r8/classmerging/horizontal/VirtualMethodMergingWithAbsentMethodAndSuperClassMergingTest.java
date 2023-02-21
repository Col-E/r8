// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** Reproduction of b/231900726. */
@RunWith(Parameterized.class)
public class VirtualMethodMergingWithAbsentMethodAndSuperClassMergingTest extends TestBase {

  @Parameter(0)
  public ClassWrapper upperMergeTarget;

  @Parameter(1)
  public ClassWrapper lowerMergeTarget;

  @Parameter(2)
  public TestParameters parameters;

  @Parameters(name = "{2}, upper merge target: {0}, lower merge target: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        ImmutableList.of(ClassWrapper.create(A.class), ClassWrapper.create(B.class)),
        ImmutableList.of(ClassWrapper.create(C.class), ClassWrapper.create(D.class)),
        getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  // Use a ClassWrapper to wrap the classes such that the string representation of the test do not
  // exceed to many characters.
  public static class ClassWrapper {
    public final Class<?> clazz;

    private ClassWrapper(Class<?> clazz) {
      this.clazz = clazz;
    }

    public static ClassWrapper create(Class<?> clazz) {
      return new ClassWrapper(clazz);
    }

    public String getTypeName() {
      return clazz.getTypeName();
    }

    public boolean is(Class<?> clazz) {
      return this.clazz == clazz;
    }

    @Override
    public String toString() {
      return clazz.getSimpleName();
    }
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        // Control the targets of the horizontal class merger.
        .addOptionsModification(
            options ->
                options.testing.horizontalClassMergingTarget =
                    (appView, candidates, target) -> {
                      if (Iterables.any(
                          candidates,
                          candidate -> candidate.getTypeName().equals(A.class.getTypeName()))) {
                        return Iterables.find(
                            candidates,
                            candidate ->
                                candidate.getTypeName().equals(upperMergeTarget.getTypeName()));
                      }
                      if (Iterables.any(
                          candidates,
                          candidate -> candidate.getTypeName().equals(C.class.getTypeName()))) {
                        return Iterables.find(
                            candidates,
                            candidate ->
                                candidate.getTypeName().equals(lowerMergeTarget.getTypeName()));
                      }
                      return target;
                    })
        // Verify that the targets are as expected.
        .addHorizontallyMergedClassesInspector(
            inspector ->
                inspector
                    .applyIf(
                        upperMergeTarget.is(A.class),
                        i -> i.assertMergedInto(B.class, A.class),
                        i -> i.assertMergedInto(A.class, B.class))
                    .applyIf(
                        lowerMergeTarget.is(C.class),
                        i -> i.assertMergedInto(D.class, C.class),
                        i -> i.assertMergedInto(C.class, D.class))
                    .assertNoOtherClassesMerged())
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A", "B", "C", "A");
  }

  static class Main {

    public static void main(String[] args) {
      new A().m();
      new B().m();
      new C().m();
      new D().m();
    }
  }

  static class A {

    void m() {
      System.out.println("A");
    }
  }

  static class B {

    void m() {
      System.out.println("B");
    }
  }

  static class C extends A {

    @Override
    void m() {
      System.out.println("C");
    }
  }

  static class D extends A {}
}
