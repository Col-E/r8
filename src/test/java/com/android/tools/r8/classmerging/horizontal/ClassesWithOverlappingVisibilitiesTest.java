// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPackagePrivate;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPublic;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoAccessModification;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;

public class ClassesWithOverlappingVisibilitiesTest extends HorizontalClassMergingTestBase {
  public ClassesWithOverlappingVisibilitiesTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoAccessModificationAnnotationsForMembers()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("foo A", "FOO B", "FOO C", "foo D", "FOO E")
        .inspect(
            codeInspector -> {
              ClassSubject aClassSubject = codeInspector.clazz(A.class);
              assertThat(aClassSubject, isPresent());
              MethodSubject methodSubject = aClassSubject.method("void", "foo");
              assertThat(methodSubject, isPackagePrivate());

              ClassSubject bClassSubject = codeInspector.clazz(B.class);
              assertThat(bClassSubject, isPresent());
              methodSubject = bClassSubject.method("void", "foo$bridge");
              assertThat(methodSubject, isPackagePrivate());

              assertThat(codeInspector.clazz(C.class), isAbsent());

              ClassSubject dClassSubject = codeInspector.clazz(D.class);
              assertThat(dClassSubject, isPresent());
              methodSubject = dClassSubject.method("void", "foo$bridge");
              assertThat(methodSubject, isPublic());

              ClassSubject eClassSubject = codeInspector.clazz(E.class);
              assertThat(eClassSubject, isAbsent());
            });
  }

  @NeverClassInline
  public static class A {
    @NeverInline
    @NoAccessModification
    void foo() {
      System.out.println("foo A");
    }
  }

  @NeverClassInline
  public static class B extends A {
    public B() {
      foo();
    }

    @Override
    @NeverInline
    @NoAccessModification
    void foo() {
      System.out.println("FOO B");
    }
  }

  // This class should be merged into B, as the method foo is package private both classes.
  @NeverClassInline
  public static class C extends A {
    public C() {
      foo();
    }

    @Override
    @NeverInline
    @NoAccessModification
    void foo() {
      System.out.println("FOO C");
    }
  }

  // This class can only be merged into E as D#foo is public, while A#foo is package private.
  @NeverClassInline
  public static class D {
    @NeverInline
    public void foo() {
      System.out.println("foo D");
    }
  }

  @NeverClassInline
  public static class E {
    public E() {
      foo();
    }

    @NeverInline
    public void foo() {
      System.out.println("FOO E");
    }
  }

  public static class Main {
    public static void main(String[] args) {
      A a = new A();
      a.foo();
      B b = new B();
      C c = new C();
      D d = new D();
      d.foo();
      E e = new E();
    }
  }
}
