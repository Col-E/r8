// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import org.junit.Test;

public class RemapFieldTest extends HorizontalClassMergingTestBase {
  public RemapFieldTest(TestParameters parameters) {
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
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A", "B", "foo: foo c", "B", "foo: bar d")
        .inspect(
            codeInspector -> {
              assertThat(codeInspector.clazz(A.class), isPresent());
              assertThat(codeInspector.clazz(B.class), isAbsent());
              assertThat(codeInspector.clazz(C.class), isPresent());
              assertThat(codeInspector.clazz(D.class), isAbsent());
            });
  }

  @NeverClassInline
  public static class A {
    public A() {
      System.out.println("A");
    }
  }

  @NeverClassInline
  public static class B {
    public B() {
      System.out.println("B");
    }

    public void foo(String s) {
      System.out.println("foo: " + s);
    }
  }

  @NeverClassInline
  public static class C {
    B b;

    public C(B b) {
      this.b = b;
    }

    @NeverInline
    public void foo() {
      b.foo("foo c");
    }
  }

  @NeverClassInline
  public static class D {
    B b;

    public D(B b) {
      this.b = b;
    }

    @NeverInline
    public void bar() {
      b.foo("bar d");
    }
  }

  public static class Main {
    public static void main(String[] args) {
      new A();
      new C(new B()).foo();
      new D(new B()).bar();
    }
  }
}
