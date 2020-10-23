// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class EventuallyNonTargetedMethodTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("A::foo", "C::bar");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public EventuallyNonTargetedMethodTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .enableInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .enableNeverClassInliningAnnotations()
        .addInnerClasses(EventuallyNonTargetedMethodTest.class)
        .addKeepMainRule(Main.class)
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED)
        .inspect(this::checkIsFooPresent);
  }

  private void checkIsFooPresent(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(C.class);
    assertThat(classSubject, isPresent());
    // TODO(b/150445487): Member rebinding will rewrite B::foo to A::foo causing C::foo to remain.
    assertThat(classSubject.uniqueMethodWithName("foo"), isPresent());
  }

  @NoVerticalClassMerging
  private static class A {
    @NeverInline
    public void foo() {
      System.out.println("A::foo");
    }
  }

  @NoVerticalClassMerging
  @NeverClassInline
  private static class B extends A {
    // No override of foo, but B::foo will be the only target.
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  private static class C extends A {

    // Non-targeted override.
    @Override
    public void foo() {
      System.out.println("C::foo");
    }

    @NeverInline
    public void bar() {
      System.out.println("C::bar");
    }
  }

  private static class Main {
    static boolean effectivelyFinalFalse = false;

    public static void main(String[] args) {
      new B().foo();
      new C().bar();
      if (effectivelyFinalFalse) {
        // First round of tree shaking the below reference to A::foo will keep it live.
        // This branch will then be removed and it should be possible to conclude C::foo dead.
        new A().foo();
      }
    }
  }
}
