// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.accessrelaxation;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InterfaceMethodAndSiblingConsistentRenamingPublicizerTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              // Check that B.foo(), C.foo() and I.foo() are all present in the output.
              ClassSubject bClassSubject = inspector.clazz(B.class);
              assertThat(bClassSubject, isPresent());

              MethodSubject bMethodSubject = bClassSubject.uniqueMethodWithOriginalName("foo");
              assertThat(bMethodSubject, isPresent());

              ClassSubject cClassSubject = inspector.clazz(C.class);
              assertThat(cClassSubject, isPresent());

              MethodSubject cMethodSubject = cClassSubject.uniqueMethodWithOriginalName("foo");
              assertThat(cMethodSubject, isPresent());

              ClassSubject iClassSubject = inspector.clazz(I.class);
              assertThat(iClassSubject, isPresent());

              MethodSubject iMethodSubject = iClassSubject.uniqueMethodWithOriginalName("foo");
              assertThat(iMethodSubject, isPresent());

              // Check that B.foo() is renamed to one name, and C.foo() and I.foo() is renamed to
              // another name. The reason for B.foo() being given another name is that we use a
              // single naming state for the classes, which means that we must give B.foo() another
              // name to ensure we do not introduce new method overriding relationships from
              // publicizing.
              assertNotEquals(bMethodSubject.getFinalName(), cMethodSubject.getFinalName());
              assertEquals(cMethodSubject.getFinalName(), iMethodSubject.getFinalName());
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("B", "C", "C");
  }

  static class Main {

    public static void main(String[] args) {
      new B().foo();
      new C().foo();
      I i = System.currentTimeMillis() > 0 ? new D() : new E();
      i.foo();
    }
  }

  interface I {

    void foo();
  }

  static class A {}

  @NeverClassInline
  @NoHorizontalClassMerging
  static class B extends A {

    @NeverInline
    private void foo() {
      System.out.println("B");
    }
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  static class C extends A {

    // Implements I.foo().
    @NeverInline
    public void foo() {
      System.out.println("C");
    }
  }

  @NoHorizontalClassMerging
  static class D extends C implements I {}

  @NoHorizontalClassMerging
  static class E extends C implements I {

    @Override
    public void foo() {
      System.out.println("E");
    }
  }
}
