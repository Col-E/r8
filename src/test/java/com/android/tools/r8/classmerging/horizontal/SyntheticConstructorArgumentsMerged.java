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

public class SyntheticConstructorArgumentsMerged extends HorizontalClassMergingTestBase {
  public SyntheticConstructorArgumentsMerged(TestParameters parameters) {
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
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("5", "42")
        .inspect(
            codeInspector -> {
              assertThat(codeInspector.clazz(A.class), isPresent());
              assertThat(codeInspector.clazz(B.class), not(isPresent()));
            });
  }

  @NeverClassInline
  public static class A {
    public A(B b) {
      b.print(42);
    }
  }

  @NeverClassInline
  public static class B {
    public B(B b) {
      if (b != null) {
        b.print(5);
      }
    }

    @NeverInline
    void print(int v) {
      System.out.println(v);
    }
  }

  public static class Main {
    public static void main(String[] args) {
      B b = new B(null);
      B b1 = new B(b);
      A a = new A(b);
    }
  }
}
