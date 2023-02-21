// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runners.Parameterized;

public class SynchronizedMethodMergingTest extends HorizontalClassMergingTestBase {

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public SynchronizedMethodMergingTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters)
        .addHorizontallyMergedClassesInspector(
            inspector ->
                inspector.assertMergedInto(B.class, A.class).assertMergedInto(D.class, C.class))
        .compile()
        .inspect(
            inspector -> {
              ClassSubject aClassSubject = inspector.clazz(A.class);
              assertThat(aClassSubject, isPresent());

              MethodSubject synchronizedMethodSubject =
                  aClassSubject.uniqueMethodWithOriginalName("m$bridge");
              assertThat(synchronizedMethodSubject, isPresent());
              assertTrue(synchronizedMethodSubject.isSynchronized());

              ClassSubject cClassSubject = inspector.clazz(C.class);
              assertThat(cClassSubject, isPresent());

              MethodSubject unsynchronizedMethodSubject =
                  cClassSubject.uniqueMethodWithOriginalName("m$bridge");
              assertThat(unsynchronizedMethodSubject, isPresent());
              assertFalse(unsynchronizedMethodSubject.isSynchronized());
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A", "B", "C", "D");
  }

  public static class Main {
    public static void main(String[] args) {
      new A().m();
      new B().m();
      new C().m();
      new D().m();
    }
  }

  @NeverClassInline
  public static class A {

    @NeverInline
    public synchronized void m() {
      System.out.println("A");
    }
  }

  @NeverClassInline
  public static class B {

    @NeverInline
    public synchronized void m() {
      System.out.println("B");
    }
  }

  @NeverClassInline
  public static class C {

    @NeverInline
    public void m() {
      System.out.println("C");
    }
  }

  @NeverClassInline
  public static class D {

    @NeverInline
    public void m() {
      System.out.println("D");
    }
  }
}
