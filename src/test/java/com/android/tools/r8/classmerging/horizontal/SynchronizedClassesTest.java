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
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import org.junit.Test;

public class SynchronizedClassesTest extends HorizontalClassMergingTestBase {
  public SynchronizedClassesTest(TestParameters parameters, boolean enableHorizontalClassMerging) {
    super(parameters, enableHorizontalClassMerging);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addOptionsModification(
            options -> {
              options.enableHorizontalClassMerging = enableHorizontalClassMerging;
            })
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("foo", "b", "bar", "1", "true")
        .inspect(
            codeInspector -> {
              assertThat(codeInspector.clazz(A.class), isPresent());
              assertThat(codeInspector.clazz(B.class), isPresent());
              if (enableHorizontalClassMerging) {
                // C has been merged into A.
                assertThat(codeInspector.clazz(C.class), not(isPresent()));
                assertThat(codeInspector.clazz(A.class).init("long"), isPresent());

                // D has been merged into B.
                assertThat(codeInspector.clazz(D.class), not(isPresent()));
                ClassSubject bClassSubject = codeInspector.clazz(B.class);
                assertThat(bClassSubject.init("boolean"), isPresent());
              } else {
                assertThat(codeInspector.clazz(A.class), isPresent());
                assertThat(codeInspector.clazz(B.class), isPresent());
              }
            });
  }

  @NeverClassInline
  public static class A {
    @NeverInline
    static synchronized void foo() {
      System.out.println("foo");
    }
  }

  @NeverClassInline
  public static class B {
    public B(String foo) {
      System.out.println(foo);
    }

    @NeverInline
    void bar() {
      synchronized (B.class) {
        System.out.println("bar");
      }
    }
  }

  @NeverClassInline
  public static class C {
    public C(long v) {
      System.out.println(v);
    }
  }

  @NeverClassInline
  public static class D {
    public D(boolean v) {
      System.out.println(v);
    }
  }

  public static class Main {
    public static void main(String[] args) {
      A a = new A();
      A.foo();
      B b = new B("b");
      b.bar();
      C c = new C(1);
      D d = new D(true);
    }
  }
}
