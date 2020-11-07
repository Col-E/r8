// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.notIf;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import org.junit.Test;

public class TreeFixerSubClassCollisionTest extends HorizontalClassMergingTestBase {
  public TreeFixerSubClassCollisionTest(
      TestParameters parameters, boolean enableHorizontalClassMerging) {
    super(parameters, enableHorizontalClassMerging);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .noMinification()
        .addOptionsModification(
            options -> options.enableHorizontalClassMerging = enableHorizontalClassMerging)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(
            "print a: foo c a", "print b: foo c b", "print b: foo c b", "print b: foo d b")
        .inspect(
            codeInspector -> {
              assertThat(codeInspector.clazz(A.class), isPresent());
              assertThat(
                  codeInspector.clazz(B.class), notIf(isPresent(), enableHorizontalClassMerging));

              ClassSubject cClassSubject = codeInspector.clazz(C.class);
              assertThat(cClassSubject, isPresent());
              // C#foo(B) is renamed to C#foo$1(A).
              if (enableHorizontalClassMerging) {
                assertThat(cClassSubject.uniqueMethodWithFinalName("foo"), isPresent());
                assertThat(cClassSubject.uniqueMethodWithFinalName("foo$1"), isPresent());
              }

              ClassSubject dClassSubject = codeInspector.clazz(D.class);
              assertThat(dClassSubject, isPresent());
              // D#foo$1(B) is renamed to D#foo$2(A).
              assertThat(
                  dClassSubject.uniqueMethodWithFinalName(
                      enableHorizontalClassMerging ? "foo$1$1" : "foo$1"),
                  isPresent());
            });
  }

  @NeverClassInline
  public static class A {
    @NeverInline
    public void print(String v) {
      System.out.println("print a: " + v);
    }
  }

  @NeverClassInline
  public static class B {
    @NeverInline
    public void print(String v) {
      System.out.println("print b: " + v);
    }
  }

  @NoHorizontalClassMerging
  @NeverClassInline
  public static class C {
    @NeverInline
    public void foo(A a) {
      a.print("foo c a");
    }

    @NeverInline
    public void foo(B b) {
      b.print("foo c b");
    }
  }

  @NoVerticalClassMerging
  @NeverClassInline
  public static class D extends C {
    @NeverInline
    public void foo$1(B b) {
      b.print("foo d b");
    }
  }

  public static class Main {
    public static void main(String[] args) {
      A a = new A();
      B b = new B();
      C c = new C();
      c.foo(a);
      c.foo(b);
      D d = new D();
      d.foo(b);
      d.foo$1(b);
    }
  }
}
