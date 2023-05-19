// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestParameters;
import org.junit.Test;

public class VerticallyMergedClassTest extends HorizontalClassMergingTestBase {
  public VerticallyMergedClassTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableNoHorizontalClassMergingAnnotations()
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("b", "a", "c")
        .inspect(
            codeInspector -> {
              assertThat(codeInspector.clazz(A.class), not(isPresent()));
              assertThat(codeInspector.clazz(B.class), isPresent());
              assertThat(codeInspector.clazz(C.class), isPresent());
            });
  }

  public static class A {
    public void print() {
      System.out.println("a");
    }
  }

  @NoHorizontalClassMerging
  @NeverClassInline
  public static class B extends A {
    public B() {
      System.out.println("b");
    }
  }

  @NeverClassInline
  public static class C {

    @NeverInline
    public C() {
      System.out.println("c");
    }
  }

  public static class Main {
    @NeverInline
    static void printA(A a) {
      a.print();
    }

    public static void main(String[] args) {
      printA(new B());
      new C();
    }
  }
}
