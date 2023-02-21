// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EquivalentConstructorsWithClassIdAndDifferentArgumentOrderMergingTest
    extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection params() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public EquivalentConstructorsWithClassIdAndDifferentArgumentOrderMergingTest(
      TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addHorizontallyMergedClassesInspector(
            inspector ->
                inspector.assertIsCompleteMergeGroup(A.class, B.class).assertNoOtherClassesMerged())
        .enableNeverClassInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject aClassSubject = inspector.clazz(A.class);
              assertThat(aClassSubject, isPresent());

              // TODO(b/189296638): Enable constructor merging by changing the constructor
              // arguments.
              assertEquals(
                  2, aClassSubject.allMethods(FoundMethodSubject::isInstanceInitializer).size());
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("C0", "D1");
  }

  static class Main {

    public static void main(String[] args) {
      System.out.println(new A(0, new C()));
      System.out.println(new B(new D(), 1));
    }
  }

  @NeverClassInline
  static class A {

    private final C c;
    private final int i;

    A(int i, C c) {
      this.c = c;
      this.i = i;
    }

    @Override
    public String toString() {
      return c.toString() + i;
    }
  }

  @NeverClassInline
  static class B {

    private final D d;
    private final int i;

    B(D d, int i) {
      this.d = d;
      this.i = i;
    }

    @Override
    public String toString() {
      return d.toString() + i;
    }
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  static class C {

    @Override
    public String toString() {
      return "C";
    }
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  static class D {

    @Override
    public String toString() {
      return "D";
    }
  }
}
