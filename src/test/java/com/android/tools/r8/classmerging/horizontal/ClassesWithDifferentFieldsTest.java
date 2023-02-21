/*
 *  // Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
 *  // for details. All rights reserved. Use of this source code is governed by a
 *  // BSD-style license that can be found in the LICENSE file.
 */

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import org.junit.Test;

public class ClassesWithDifferentFieldsTest extends HorizontalClassMergingTestBase {
  public ClassesWithDifferentFieldsTest(TestParameters parameters) {
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
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A. v: a", "B. i: 2")
        .inspect(
            codeInspector -> {
              assertThat(codeInspector.clazz(A.class), isPresent());
              assertThat(codeInspector.clazz(B.class), isAbsent());
            });
  }

  @NeverClassInline
  public static class A {
    public String v;

    public A(String v) {
      this.v = v;
    }

    @NeverInline
    public void foo() {
      System.out.println("A. v: " + v);
    }
  }

  @NeverClassInline
  public static class B {
    public Integer i;

    public B(Integer i) {
      this.i = i;
    }

    @NeverInline
    public void foo() {
      System.out.println("B. i: " + i);
    }
  }

  public static class Main {
    public static void main(String[] args) {
      new A("a").foo();
      new B(2).foo();
    }
  }
}
