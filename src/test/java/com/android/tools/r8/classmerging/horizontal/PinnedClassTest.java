// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.TestParameters;
import org.junit.Test;

public class PinnedClassTest extends HorizontalClassMergingTestBase {
  public PinnedClassTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepClassRules(B.class)
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("a", "b", "true", "true")
        .inspect(
            codeInspector -> {
              assertThat(codeInspector.clazz(A.class), isPresent());
              assertThat(codeInspector.clazz(B.class), isPresent());
            });
  }

  @NeverClassInline
  public static class A {
    public A() {
      System.out.println("a");
    }
  }

  @NeverClassInline
  public static class B {
    public B(String s) {
      System.out.println(s);
    }
  }

  public static class Main {
    public static void main(String[] args) {
      A a = new A();
      B b = new B("b");

      System.out.println(b.getClass().getSimpleName().equals("PinnedClassTest$B"));
      System.out.println(!a.getClass().getSimpleName().equals("PinnedClassTest$B"));
    }
  }
}
