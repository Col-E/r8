// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.accessrelaxation;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPrivate;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPublic;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
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
public class PrivateShadowOfPrivateMethodTest extends TestBase {

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
        .allowAccessModification()
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              // Check that A.foo() is publicized.
              ClassSubject aClassSubject = inspector.clazz(A.class);
              assertThat(aClassSubject, isPresent());

              MethodSubject aMethodSubject = aClassSubject.uniqueMethodWithOriginalName("foo");
              assertThat(aMethodSubject, isPresent());
              assertThat(aMethodSubject, isPublic());

              // Check that B.foo is still private.
              ClassSubject bClassSubject = inspector.clazz(B.class);
              assertThat(bClassSubject, isPresent());

              MethodSubject bMethodSubject = bClassSubject.uniqueMethodWithOriginalName("foo");
              assertThat(bMethodSubject, isPresent());
              assertThat(bMethodSubject, isPrivate());

              // Verify that the two methods still have the same name.
              assertEquals(aMethodSubject.getFinalName(), bMethodSubject.getFinalName());
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A", "B", "A", "A");
  }

  static class Main {

    public static void main(String[] args) {
      new A().foo();
      new B().foo();
      A a = System.currentTimeMillis() > 0 ? new A() : new B();
      a.foo();
      A b = System.currentTimeMillis() > 0 ? new B() : new A();
      b.foo();
    }
  }

  @NeverClassInline
  @NoVerticalClassMerging
  static class A {

    @NeverInline
    private void foo() {
      System.out.println("A");
    }
  }

  @NeverClassInline
  static class B extends A {

    @NeverInline
    private void foo() {
      System.out.println("B");
    }
  }
}
