// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNot.not;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.TestParameters;
import org.junit.Test;

public class OverlappingConstructorsTest extends HorizontalClassMergingTestBase {

  public OverlappingConstructorsTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .inspect(
            codeInspector -> {
              assertThat(codeInspector.clazz(A.class), isPresent());
              assertThat(codeInspector.clazz(B.class), not(isPresent()));
              assertThat(codeInspector.clazz(C.class), not(isPresent()));
            });
  }

  @NeverClassInline
  public static class A {
    public A(String s) {
      System.out.println(s);
    }
  }

  @NeverClassInline
  public static class B {
    public B(String s) {
      System.out.println(s);
    }

    public B(boolean b) {
      System.out.println(b);
    }
  }

  @NeverClassInline
  public static class C {
    public C(boolean b) {
      System.out.println(b);
    }
  }

  public static class Main {
    public static void main(String[] args) {
      A a = new A("foo");
      System.out.println(a);
      B b1 = new B("");
      System.out.println(b1);
      B b2 = new B(false);
      System.out.println(b2);
      C c = new C(true);
      System.out.println(c);
    }
  }
}
