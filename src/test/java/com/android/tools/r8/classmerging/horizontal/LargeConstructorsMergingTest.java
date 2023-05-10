// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ir.optimize.Inliner.Reason;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.google.common.collect.ImmutableSet;
import org.junit.Test;

public class LargeConstructorsMergingTest extends HorizontalClassMergingTestBase {
  public LargeConstructorsMergingTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addOptionsModification(
            options -> {
              options.testing.validInliningReasons = ImmutableSet.of(Reason.FORCE);
              options.testing.verificationSizeLimitInBytesOverride = 4;
            })
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters)
        .addHorizontallyMergedClassesInspector(
            inspector ->
                inspector.assertMergedInto(B.class, A.class).assertMergedInto(C.class, A.class))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("a: 1", "b: 2", "c: 3")
        .inspect(
            codeInspector -> {
              ClassSubject aClassSubject = codeInspector.clazz(A.class);
              assertThat(aClassSubject, isPresent());
              assertThat(codeInspector.clazz(B.class), isAbsent());
              assertThat(codeInspector.clazz(C.class), isAbsent());

              // There should be three constructors on class A after merging.
              assertEquals(3, aClassSubject.allMethods().size());
            });
  }

  @NeverClassInline
  public static class A {
    public A(int v) {
      System.out.println("a: " + v);
    }
  }

  @NeverClassInline
  public static class B {
    public B(int v) {
      System.out.println("b: " + v);
    }
  }

  @NeverClassInline
  public static class C {
    public C(int v) {
      System.out.println("c: " + v);
    }
  }

  public static class Main {
    public static void main(String[] args) {
      new A(1);
      new B(2);
      new C(3);
    }
  }
}
