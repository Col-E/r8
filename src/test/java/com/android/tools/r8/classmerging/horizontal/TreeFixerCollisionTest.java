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
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestParameters;
import org.junit.Test;

public class TreeFixerCollisionTest extends HorizontalClassMergingTestBase {
  public TreeFixerCollisionTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(
            "print a: foo a", "print b: foo b", "print d: foo a", "print e: foo b")
        .inspect(
            codeInspector -> {
              assertThat(codeInspector.clazz(A.class), isPresent());
              assertThat(codeInspector.clazz(B.class), isAbsent());
              assertThat(codeInspector.clazz(Group2.class), isPresent());
              assertThat(codeInspector.clazz(C.class), isPresent());
              assertThat(codeInspector.clazz(D.class), isPresent());
              assertThat(codeInspector.clazz(E.class), isAbsent());
            });
  }

  @NeverClassInline
  public static class A {
    @NeverInline
    public String foo() {
      return "foo a";
    }
  }

  @NeverClassInline
  public static class B {

    @NeverInline
    public String foo() {
      return "foo b";
    }
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  public static class C {
    @NeverInline
    public void print(A a) {
      System.out.println("print a: " + a.foo());
    }

    @NeverInline
    public void print(B a) {
      System.out.println("print b: " + a.foo());
    }
  }

  @NoVerticalClassMerging
  @NoHorizontalClassMerging
  public static class Group2 {}

  @NeverClassInline
  public static class D extends Group2 {
    @NeverInline
    public void print(A a) {
      System.out.println("print d: " + a.foo());
    }
  }

  @NeverClassInline
  public static class E extends Group2 {
    @NeverInline
    public void print(B b) {
      System.out.println("print e: " + b.foo());
    }
  }

  public static class Main {
    public static void main(String[] args) {
      A a = new A();
      B b = new B();
      C c = new C();
      c.print(a);
      c.print(b);
      new D().print(a);
      new E().print(b);
    }
  }
}
