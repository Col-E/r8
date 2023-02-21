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

public class SuperConstructorCallsVirtualMethodTest extends HorizontalClassMergingTestBase {
  public SuperConstructorCallsVirtualMethodTest(TestParameters parameters) {
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
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("5", "foo hello", "B", "bar world", "5", "B")
        .inspect(
            codeInspector -> {
              assertThat(codeInspector.clazz(Parent.class), isPresent());
              assertThat(codeInspector.clazz(A.class), isPresent());
              assertThat(codeInspector.clazz(B.class), not(isPresent()));
            });
  }

  @NeverClassInline
  public static class Parent {
    String field;

    public Parent(String v) {
      this.field = v;
      print();
    }

    @NeverInline
    public void print() {
      System.out.println(field);
    }
  }

  @NeverClassInline
  public static class A extends Parent {
    public A(String arg) {
      super(arg);
      System.out.println("foo " + arg);
    }

    @NeverInline
    @Override
    public void print() {
      System.out.println(5);
    }
  }

  @NeverClassInline
  public static class B extends Parent {
    public B(String arg) {
      super("B");
      System.out.println("bar " + arg);
    }
  }

  public static class Main {
    public static void main(String[] args) {
      A a = new A("hello");
      B b = new B("world");
      a.print();
      b.print();
    }
  }
}
