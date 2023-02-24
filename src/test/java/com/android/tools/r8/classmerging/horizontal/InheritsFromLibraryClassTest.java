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
import java.util.ArrayList;
import org.junit.Test;

public class InheritsFromLibraryClassTest extends HorizontalClassMergingTestBase {
  public InheritsFromLibraryClassTest(TestParameters parameters) {
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
        .assertSuccessWithOutputLines("a", "foo a", "b", "foo")
        .inspect(
            codeInspector -> {
              assertThat(codeInspector.clazz(Parent.class), isPresent());
              assertThat(codeInspector.clazz(A.class), isPresent());
              assertThat(codeInspector.clazz(B.class), isAbsent());
              assertThat(codeInspector.clazz(C.class), isPresent());
            });
  }

  public static class Parent {
    @NeverInline
    public void foo() {
      System.out.println("foo");
    }
  }

  @NeverClassInline
  public static class A extends Parent {
    public A() {
      System.out.println("a");
    }

    @NeverInline
    public void foo() {
      System.out.println("foo a");
    }
  }

  @NeverClassInline
  public static class B extends Parent {
    public B() {
      System.out.println("b");
    }
  }

  @NeverClassInline
  public static class C extends ArrayList<Object> {
    public C() {}

    public void fooB(B b) {
      b.foo();
    }
  }

  public static class Main {
    public static void main(String[] args) {
      new A().foo();
      new C().fooB(new B());
    }
  }
}
