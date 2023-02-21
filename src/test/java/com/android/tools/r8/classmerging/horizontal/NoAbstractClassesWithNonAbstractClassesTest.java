// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoMethodStaticizing;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestParameters;
import org.junit.Test;

public class NoAbstractClassesWithNonAbstractClassesTest extends HorizontalClassMergingTestBase {
  public NoAbstractClassesWithNonAbstractClassesTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoMethodStaticizingAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("bar", "foo c", "foo d", "foo c")
        .inspect(
            codeInspector -> {
              assertThat(codeInspector.clazz(A.class), isPresent());
              assertThat(codeInspector.clazz(B.class), isPresent());
              assertThat(codeInspector.clazz(C.class), isPresent());
              assertThat(codeInspector.clazz(D.class), isAbsent());
            });
  }

  @NoVerticalClassMerging
  public abstract static class A {
    public abstract void foo();
  }

  @NeverClassInline
  public static class B {
    @NeverInline
    @NoMethodStaticizing
    public void bar() {
      System.out.println("bar");
    }
  }

  @NeverClassInline
  public static class C extends A {

    @Override
    @NeverInline
    public void foo() {
      System.out.println("foo c");
    }
  }

  @NeverClassInline
  public static class D extends A {

    @Override
    @NeverInline
    public void foo() {
      System.out.println("foo d");
    }
  }

  public static class Main {
    @NeverInline
    public static void foo(A a) {
      a.foo();
    }

    public static void main(String[] args) {
      new B().bar();
      C c = new C();

      // This test also checks that the synthesized C#foo does not try to call the abstract A#foo.
      foo(c);
      foo(new D());
      c.foo();
    }
  }
}
