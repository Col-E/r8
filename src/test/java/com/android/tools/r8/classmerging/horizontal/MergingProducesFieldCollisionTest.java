// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import org.junit.Test;

public class MergingProducesFieldCollisionTest extends HorizontalClassMergingTestBase {
  public MergingProducesFieldCollisionTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void testR8() throws Exception {
    byte[] transformedC = transformer(C.class).renameAndRemapField("v$1", "v").transform();

    testForR8(parameters.getBackend())
        .addKeepMainRule(Main.class)
        .addProgramClassFileData(transformedC)
        .addProgramClasses(Parent.class, A.class, B.class, Main.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccess()
        .inspect(
            codeInspector -> {
              assertThat(codeInspector.clazz(Parent.class), isPresent());

              ClassSubject aClassSubject = codeInspector.clazz(A.class);
              assertThat(aClassSubject, isPresent());

              ClassSubject bClassSubject = codeInspector.clazz(B.class);
              assertThat(bClassSubject, isAbsent());

              ClassSubject cClassSubject = codeInspector.clazz(C.class);
              assertThat(cClassSubject, isPresent());

              assertEquals(
                  cClassSubject.allFields().get(0).type(), cClassSubject.allFields().get(1).type());
            });
  }

  public static class Parent {
    @NeverInline
    public void foo() {
      System.out.println("foo");
    }
  }

  @NeverClassInline
  public static class A extends Parent {
    public A() {
      System.out.println("b");
    }
  }

  @NeverClassInline
  public static class B extends Parent {
    public B() {
      System.out.println("b");
    }

    @NeverInline
    public void foo() {
      System.out.println("foo b");
    }
  }

  @NeverClassInline
  public static class C {
    A v;
    B v$1; // This field is renamed to v.

    public C(A a, B b) {
      v = a;
      v$1 = b;
    }

    @NeverInline
    public void foo() {
      v.foo();
      v$1.foo();
    }
  }

  public static class Main {
    public static void main(String[] args) {
      new C(new A(), new B()).foo();
    }
  }
}
