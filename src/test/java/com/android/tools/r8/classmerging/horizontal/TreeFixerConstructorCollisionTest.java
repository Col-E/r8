// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestParameters;
import org.junit.Test;

public class TreeFixerConstructorCollisionTest extends HorizontalClassMergingTestBase {
  public TreeFixerConstructorCollisionTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addHorizontallyMergedClassesInspector(
            inspector ->
                inspector.assertIsCompleteMergeGroup(A.class, B.class).assertNoOtherClassesMerged())
        .addOptionsModification(
            options -> options.inlinerOptions().setEnableConstructorInlining(false))
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(
            "constructor a",
            "constructor 2 a",
            "constructor b",
            "constructor 2 b",
            "constructor c a: foo a: c1",
            "constructor c b: foo b: c2")
        .inspect(
            codeInspector -> {
              assertThat(codeInspector.clazz(A.class), isPresent());
              assertThat(codeInspector.clazz(B.class), isAbsent());
              assertThat(codeInspector.clazz(C.class), isPresent());
            });
  }

  @NeverClassInline
  public static class A {
    public A() {
      System.out.println("constructor a");
    }

    public A(A a) {
      System.out.println("constructor 2 a");
    }

    @NeverInline
    public String foo(String v) {
      return "foo a: " + v;
    }
  }

  @NeverClassInline
  public static class B {
    public B() {
      System.out.println("constructor b");
    }

    public B(B b) {
      System.out.println("constructor 2 b");
    }

    @NeverInline
    public String foo(String v) {
      return "foo b: " + v;
    }
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  public static class C {
    public C(A a, String v) {
      System.out.println("constructor c a: " + a.foo(v));
    }

    public C(B b, String v) {
      System.out.println("constructor c b: " + b.foo(v));
    }
  }

  public static class Main {
    public static void main(String[] args) {
      A a = new A();
      a = new A(a);
      B b = new B();
      b = new B(b);
      new C(a, "c1");
      new C(b, "c2");
    }
  }
}
