// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.TestParameters;
import org.junit.Test;

public class InnerOuterClassesTest extends HorizontalClassMergingTestBase {
  public InnerOuterClassesTest(TestParameters parameters, boolean enableHorizontalClassMerging) {
    super(parameters, enableHorizontalClassMerging);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addOptionsModification(
            options -> options.enableHorizontalClassMerging = enableHorizontalClassMerging)
        .enableNeverClassInliningAnnotations()
        .addKeepAttributes("InnerClasses", "EnclosingMethod")
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("a", "b", "c", "d")
        .inspect(
            codeInspector -> {
              assertThat(codeInspector.clazz(A.class), isPresent());
              assertThat(codeInspector.clazz(A.B.class), isPresent());
              assertThat(codeInspector.clazz(C.class), isPresent());
              assertThat(codeInspector.clazz(A.D.class), isPresent());
            });
  }

  @NeverClassInline
  public static class A {
    public A() {
      System.out.println("a");
    }

    @NeverClassInline
    public static class B {
      public B() {
        System.out.println("b");
      }
    }

    @NeverClassInline
    public static class D {
      public D() {
        System.out.println("d");
      }
    }
  }

  @NeverClassInline
  public static class C {
    public C() {
      System.out.println("c");
    }
  }

  public static class Main {
    public static void main(String[] args) {
      A a = new A();
      A.B b = new A.B();
      C c = new C();
      A.D d = new A.D();
    }
  }
}
