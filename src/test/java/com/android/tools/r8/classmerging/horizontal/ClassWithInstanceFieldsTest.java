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

public class ClassWithInstanceFieldsTest extends HorizontalClassMergingTestBase {
  public ClassWithInstanceFieldsTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableNeverClassInliningAnnotations()
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .addHorizontallyMergedClassesInspector(
            inspector -> inspector.assertMergedInto(B.class, A.class))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A. field: 5, v: a, j: 1", "B. field: b, v: 2, j: 3")
        .inspect(
            codeInspector -> {
              assertThat(codeInspector.clazz(A.class), isPresent());
              assertThat(codeInspector.clazz(B.class), isAbsent());
            });
  }

  @NeverClassInline
  public static class A {
    public int field;
    public String v;
    public int j;

    public A(int field, String v, int j) {
      this.field = field;
      this.v = v;
      this.j = j;
    }

    @NeverInline
    public void foo() {
      System.out.println("A. field: " + field + ", v: " + v + ", j: " + j);
    }
  }

  @NeverClassInline
  public static class B {
    public String field;
    public int v;
    public int j;

    public B(String field, int v, int j) {
      this.field = field;
      this.v = v;
      this.j = j;
    }

    @NeverInline
    public void foo() {
      System.out.println("B. field: " + field + ", v: " + v + ", j: " + j);
    }
  }

  public static class Main {
    public static void main(String[] args) {
      new A(5, "a", 1).foo();
      new B("b", 2, 3).foo();
    }
  }
}
