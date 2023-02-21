// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import org.junit.Test;

public class ClassesDistinguishedByDirectCheckCastTest extends HorizontalClassMergingTestBase {
  public ClassesDistinguishedByDirectCheckCastTest(TestParameters parameters) {
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
        .assertSuccessWithOutputLines("fail", "bar")
        .inspect(
            codeInspector -> {
              // The two classes should not be merged.
              assertThat(codeInspector.clazz(A.class), isPresent());
              assertThat(codeInspector.clazz(B.class), isPresent());
            });
  }

  @NeverClassInline
  public static class A {
    @NeverInline
    public void foo() {
      System.out.println("foo");
    }
  }

  @NeverClassInline
  public static class B {
    @NeverInline
    public void bar() {
      System.out.println("bar");
    }
  }

  public static class Main {
    @NeverInline
    public static void checkObject(Object o) {
      try {
        B b = (B) o;
        b.bar();
      } catch (ClassCastException ex) {
        System.out.println("fail");
      }
    }

    public static void main(String[] args) {
      A a = new A();
      B b = new B();
      checkObject(a);
      checkObject(b);
    }
  }
}
