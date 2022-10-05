// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isFieldOfArrayType;
import static com.android.tools.r8.utils.codeinspector.Matchers.isFieldOfType;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import org.junit.Test;

public class FieldTypeMergedTest extends HorizontalClassMergingTestBase {
  public FieldTypeMergedTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("a", "b", "bar", "bar", "bar")
        .inspect(
            codeInspector -> {
              ClassSubject aClassSubject = codeInspector.clazz(A.class);
              assertThat(aClassSubject, isPresent());
              assertThat(codeInspector.clazz(B.class), isAbsent());

              ClassSubject cClassSubject = codeInspector.clazz(C.class);
              assertThat(codeInspector.clazz(C.class), isPresent());

              FieldSubject fieldSubject = cClassSubject.uniqueFieldWithOriginalName("fieldB");
              assertThat(fieldSubject, isPresent());
              assertThat(fieldSubject, isFieldOfType(aClassSubject.getDexProgramClass().getType()));

              fieldSubject = cClassSubject.uniqueFieldWithOriginalName("fieldArrayB");
              assertThat(fieldSubject, isPresent());
              assertTrue(fieldSubject.getDexField().type.isArrayType());
              assertThat(
                  fieldSubject,
                  isFieldOfArrayType(codeInspector, aClassSubject.getDexProgramClass().getType()));
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
    public B() {
      System.out.println("b");
    }

    @NeverInline
    public void bar() {
      System.out.println("bar");
    }
  }

  @NeverClassInline
  public static class C {
    B fieldB;
    B[] fieldArrayB;

    public C(B b) {
      fieldB = b;
      fieldArrayB = new B[] {b, b};
    }

    @NeverInline
    public void foo() {
      fieldB.bar();
      for (B b : fieldArrayB) {
        b.bar();
      }
    }
  }

  public static class Main {
    public static void main(String[] args) {
      new A();
      B b = new B();
      new C(b).foo();
    }
  }
}
