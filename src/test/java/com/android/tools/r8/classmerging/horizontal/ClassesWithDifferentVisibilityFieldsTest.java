// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.readsInstanceField;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoAccessModification;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;

public class ClassesWithDifferentVisibilityFieldsTest extends HorizontalClassMergingTestBase {
  public ClassesWithDifferentVisibilityFieldsTest(TestParameters parameters) {
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
        .addHorizontallyMergedClassesInspector(
            inspector -> inspector.assertMergedInto(B.class, A.class))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(
            "a. v1: 10, v2: 20", "b. v1: 60, v2: 100", "c. v1: 210, v2: 330")
        .inspect(
            codeInspector -> {
              ClassSubject aClassSubject = codeInspector.clazz(A.class);
              assertThat(aClassSubject, isPresent());
              assertThat(codeInspector.clazz(B.class), isAbsent());
              assertThat(codeInspector.clazz(C.class), isPresent());

              FieldSubject v1Subject = aClassSubject.uniqueFieldWithOriginalName("v1");
              FieldSubject v2Subject = aClassSubject.uniqueFieldWithOriginalName("v2");

              MethodSubject methodSubject = aClassSubject.uniqueMethodWithOriginalName("getAV1");
              assertThat(methodSubject, isPresent());
              assertThat(methodSubject, readsInstanceField(v1Subject.getDexField()));

              methodSubject = aClassSubject.uniqueMethodWithOriginalName("getAV2");
              assertThat(methodSubject, isPresent());
              assertThat(methodSubject, readsInstanceField(v2Subject.getDexField()));

              // The fields v1 and v2 are swapped, because their access modifiers are swapped.
              methodSubject = aClassSubject.uniqueMethodWithOriginalName("getBV1");
              assertThat(methodSubject, isPresent());
              assertThat(methodSubject, readsInstanceField(v2Subject.getDexField()));

              methodSubject = aClassSubject.uniqueMethodWithOriginalName("getBV2");
              assertThat(methodSubject, isPresent());
              assertThat(methodSubject, readsInstanceField(v1Subject.getDexField()));
            });
  }

  @NeverClassInline
  public static class A {

    @NoAccessModification private int v1;

    public int v2;

    public A(int v) {
      v1 = v;
      v2 = 2 * v;
    }

    @NeverInline
    public int getAV1() {
      return v1;
    }

    @NeverInline
    public int getAV2() {
      return v2;
    }

    @NeverInline
    public void foo() {
      System.out.println("a. v1: " + getAV1() + ", v2: " + getAV2());
    }
  }

  @NeverClassInline
  public static class B {

    public int v1;

    @NoAccessModification private int v2;

    public B(int v) {
      v1 = 3 * v;
      v2 = 5 * v;
    }

    @NeverInline
    public int getBV1() {
      return v1;
    }

    @NeverInline
    public int getBV2() {
      return v2;
    }

    @NeverInline
    public void foo() {
      System.out.println("b. v1: " + getBV1() + ", v2: " + getBV2());
    }
  }

  @NeverClassInline
  public static class C {
    public int v1;
    public int v2;

    public C(int v) {
      v1 = 7 * v;
      v2 = 11 * v;
    }

    @NeverInline
    public void foo() {
      System.out.println("c. v1: " + v1 + ", v2: " + v2);
    }
  }

  public static class Main {
    public static void main(String[] args) {
      new A(10).foo();
      new B(20).foo();
      new C(30).foo();
    }
  }
}
