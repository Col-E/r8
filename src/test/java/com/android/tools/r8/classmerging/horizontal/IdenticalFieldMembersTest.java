// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.*;
import org.junit.Test;

public class IdenticalFieldMembersTest extends HorizontalClassMergingTestBase {
  public IdenticalFieldMembersTest(TestParameters parameters) {
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
        .assertSuccessWithOutputLines("foo A", "bar 2")
        .inspect(
            codeInspector -> {
              assertThat(codeInspector.clazz(A.class), isPresent());
              assertThat(codeInspector.clazz(B.class), isAbsent());
            });
  }

  @NeverClassInline
  public static class A {
    private String field;

    public A(String v) {
      this.field = v;
    }

    @NeverInline
    public void foo() {
      System.out.println("foo " + field);
    }
  }

  @NeverClassInline
  public static class B {
    private String field;

    public B(int v) {
      this.field = Integer.toString(v);
    }

    @NeverInline
    public void bar() {
      System.out.println("bar " + field);
    }
  }

  public static class Main {
    public static void main(String[] args) {
      A a = new A("A");
      a.foo();
      B b = new B(2);
      b.bar();
    }
  }
}
