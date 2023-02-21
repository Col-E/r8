// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNot.not;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import org.junit.Test;

public class RemapMethodTest extends HorizontalClassMergingTestBase {
  public RemapMethodTest(TestParameters parameters) {
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
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("foo", "foo", "bar", "bar")
        .inspect(
            codeInspector -> {
              assertThat(codeInspector.clazz(A.class), isPresent());
              assertThat(codeInspector.clazz(C.class), isPresent());
              assertThat(codeInspector.clazz(B.class), not(isPresent()));
              assertThat(codeInspector.clazz(D.class), not(isPresent()));
            });
  }

  @NeverClassInline
  public static class A {
    @NeverInline
    public void foo() {
      System.out.println("foo");
    }
  }

  @NeverClassInline
  public static class B {
    // TODO(b/164924717): remove non overlapping constructor requirement
    public B(String s) {}

    @NeverInline
    public void bar(D d) {
      d.bar();
    }
  }

  @NeverClassInline
  public static class Other {
    String field;

    public Other() {
      field = "";
    }
  }

  @NeverClassInline
  public static class C extends Other {
    @NeverInline
    public void foo() {
      System.out.println("foo");
    }
  }

  @NeverClassInline
  public static class D extends Other {
    public D(String s) {
      System.out.println(s);
    }

    @NeverInline
    public void bar() {
      System.out.println("bar");
    }
  }

  public static class Main {
    public static void main(String[] args) {
      A a = new A();
      a.foo();
      B b = new B("bar");
      C c = new C();
      c.foo();
      D d = new D("bar");
      b.bar(d);
    }
  }
}
