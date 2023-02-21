// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNot.not;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestParameters;
import org.junit.Test;

public class VerticalMergingPreoptimizedTest extends HorizontalClassMergingTestBase {

  public VerticalMergingPreoptimizedTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(
            "changed", "print a", "foo", "print b", "foo", "unused argument")
        .inspect(
            codeInspector -> {
              assertThat(codeInspector.clazz(Parent.class), not(isPresent()));
              assertThat(codeInspector.clazz(Changed.class), isPresent());
              assertThat(codeInspector.clazz(A.class), isPresent());
              assertThat(codeInspector.clazz(B.class), not(isPresent()));
            });
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  public static class Parent {
    @NeverInline
    public void foo() {
      System.out.println("foo");
    }
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  public static class Changed extends Parent {
    public Changed() {
      System.out.println("changed");
    }
  }

  @NeverClassInline
  public static class A {
    @NeverInline
    public void print(Parent p) {
      System.out.println("print a");
      p.foo();
    }
  }

  @NeverClassInline
  public static class B {
    @NeverInline
    public void print(Parent p) {
      System.out.println("print b");
      p.foo();
    }

    @NeverInline
    public void unusedArgument(Parent p) {
      System.out.println("unused argument");
    }
  }

  public static class Main {
    public static void main(String[] args) {
      Parent p = new Changed();
      A a = new A();
      B b = new B();
      a.print(p);
      b.print(p);
      b.unusedArgument(p);
    }
  }
}
