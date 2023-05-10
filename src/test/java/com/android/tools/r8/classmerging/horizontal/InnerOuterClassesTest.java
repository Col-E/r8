// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.onlyIf;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ir.optimize.Inliner.Reason;
import com.android.tools.r8.utils.BooleanUtils;
import com.google.common.collect.ImmutableSet;
import java.util.List;
import org.junit.Test;
import org.junit.runners.Parameterized;

public class InnerOuterClassesTest extends HorizontalClassMergingTestBase {

  @Parameterized.Parameters(name = "{0}, isCompat: {1}")
  public static List<Object[]> newData() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), BooleanUtils.values());
  }

  private final boolean isCompat;

  public InnerOuterClassesTest(TestParameters parameters, boolean isCompat) {
    super(parameters);
    this.isCompat = isCompat;
  }

  @Test
  public void testR8() throws Exception {
    (isCompat ? testForR8Compat(parameters.getBackend()) : testForR8(parameters.getBackend()))
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableNeverClassInliningAnnotations()
        .addKeepAttributeInnerClassesAndEnclosingMethod()
        .addOptionsModification(
            options -> options.testing.validInliningReasons = ImmutableSet.of(Reason.FORCE))
        .setMinApi(parameters)
        .addOptionsModification(
            options ->
                options.testing.horizontalClassMergingTarget =
                    (appView, candidates, target) -> candidates.iterator().next())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("a", "b", "c", "d")
        .inspect(
            codeInspector -> {
              assertThat(codeInspector.clazz(A.class), onlyIf(isCompat, isPresent()));
              assertThat(codeInspector.clazz(A.B.class), isPresent());
              assertThat(codeInspector.clazz(C.class), onlyIf(isCompat, isPresent()));
              assertThat(codeInspector.clazz(A.D.class), onlyIf(isCompat, isPresent()));
            });
  }

  @NeverClassInline
  public static class A {
    public A() {
      System.out.println("a");
    }

    @NeverClassInline
    public static class B {
      public B() {
        System.out.println("b");
      }
    }

    @NeverClassInline
    public static class D {
      public D() {
        System.out.println("d");
      }
    }
  }

  @NeverClassInline
  public static class C {
    public C() {
      System.out.println("c");
    }
  }

  public static class Main {
    public static void main(String[] args) {
      A a = new A();
      A.B b = new A.B();
      C c = new C();
      A.D d = new A.D();
    }
  }
}
