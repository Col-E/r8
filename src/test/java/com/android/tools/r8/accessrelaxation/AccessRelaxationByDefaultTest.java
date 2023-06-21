// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.accessrelaxation;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPublic;
import static com.android.tools.r8.utils.codeinspector.Matchers.onlyIf;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.NoVerticalClassMerging;
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
public class AccessRelaxationByDefaultTest extends TestBase {

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
        .addKeepClassRules(B.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject aClassSubject = inspector.clazz(A.class);
              assertThat(aClassSubject, isPresent());
              assertThat(aClassSubject, isPublic()); // Always publicized.

              MethodSubject fooMethodSubject = aClassSubject.uniqueMethodWithOriginalName("foo");
              assertThat(fooMethodSubject, isPresent());
              assertThat(fooMethodSubject, onlyIf(parameters.isDexRuntime(), isPublic()));

              ClassSubject bClassSubject = inspector.clazz(B.class);
              assertThat(bClassSubject, isPresent());
              assertThat(bClassSubject, not(isPublic())); // Kept, so never publicized.

              MethodSubject barMethodSubject = bClassSubject.uniqueMethodWithOriginalName("bar");
              assertThat(barMethodSubject, isPresent());
              assertThat(barMethodSubject, onlyIf(parameters.isDexRuntime(), isPublic()));

              ClassSubject cClassSubject = inspector.clazz(C.class);
              assertThat(cClassSubject, isPresent());
              assertThat(cClassSubject, isPublic()); // Always publicized.

              MethodSubject bazMethodSubject = cClassSubject.uniqueMethodWithOriginalName("baz");
              assertThat(bazMethodSubject, isPresent());
              assertThat(bazMethodSubject, isPublic()); // Always publicized.
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A.foo()", "B.bar()", "C.baz()");
  }

  static class Main {

    public static void main(String[] args) {
      new A().foo();
      new B().bar();
      new C().baz();
    }
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  @NoVerticalClassMerging
  static class A {

    @NeverInline
    void foo() {
      System.out.println("A.foo()");
    }
  }

  // @Keep
  @NeverClassInline
  static class B extends A {

    @NeverInline
    void bar() {
      System.out.println("B.bar()");
    }
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  static class C {

    @NeverInline
    void baz() {
      System.out.println("C.baz()");
    }
  }
}
