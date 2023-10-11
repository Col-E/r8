// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.Matchers.isExtending;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPackagePrivate;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPublic;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoAccessModification;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.NoUnusedInterfaceRemoval;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NonPublicOverrideOfPublicMethodAfterClassMergingTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection parameters() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addHorizontallyMergedClassesInspector(
            inspector ->
                inspector.assertIsCompleteMergeGroup(I.class, J.class).assertNoOtherClassesMerged())
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoAccessModificationAnnotationsForMembers()
        .enableNoHorizontalClassMergingAnnotations()
        .enableNoUnusedInterfaceRemovalAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject iClassSubject = inspector.clazz(I.class);
              assertThat(iClassSubject, isPresent());
              assertThat(
                  iClassSubject.uniqueMethodThatMatches(m -> !m.isInstanceInitializer()),
                  allOf(isPresent(), isPublic()));

              ClassSubject aClassSubject = inspector.clazz(A.class);
              assertThat(aClassSubject, isPresent());
              assertThat(aClassSubject, isExtending(iClassSubject));
              assertThat(
                  aClassSubject.uniqueMethodWithOriginalName("m"),
                  allOf(isPresent(), isPackagePrivate()));
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A.m()", "B.m()");
  }

  static class Main {

    public static void main(String[] args) {
      new A().m();
      (System.currentTimeMillis() > 0 ? new B() : new C()).m();
    }
  }

  @NoUnusedInterfaceRemoval
  @NoVerticalClassMerging
  abstract static class I {}

  @NoUnusedInterfaceRemoval
  @NoVerticalClassMerging
  abstract static class J {

    public abstract void m();
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  static class A extends I {

    @NeverInline
    @NoAccessModification
    void m() {
      System.out.println("A.m()");
    }
  }

  @NoHorizontalClassMerging
  static class B extends J {

    @Override
    public void m() {
      System.out.println("B.m()");
    }
  }

  @NoHorizontalClassMerging
  static class C extends J {

    @Override
    public void m() {
      System.out.println("C.m()");
    }
  }
}
