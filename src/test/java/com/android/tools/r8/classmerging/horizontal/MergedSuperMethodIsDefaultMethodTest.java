// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.notIf;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestParameters;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.junit.Test;

public class MergedSuperMethodIsDefaultMethodTest extends HorizontalClassMergingTestBase {
  public MergedSuperMethodIsDefaultMethodTest(
      TestParameters parameters, boolean enableHorizontalClassMerging) {
    super(parameters, enableHorizontalClassMerging);
  }

  @Test
  public void testR8() throws IOException, CompilationFailedException, ExecutionException {
    testForR8(parameters.getBackend())
        .addInnerClasses(this.getClass())
        .addOptionsModification(
            options -> options.enableHorizontalClassMerging = enableHorizontalClassMerging)
        .enableNoVerticalClassMergingAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableInliningAnnotations()
        .addKeepMainRule(Main.class)
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("I.foo")
        .inspect(
            codeInspector -> {
              assertThat(codeInspector.clazz(I.class), isPresent());
              assertThat(codeInspector.clazz(A.class), isPresent());
              assertThat(codeInspector.clazz(B.class), isPresent());
              assertThat(
                  codeInspector.clazz(C.class), notIf(isPresent(), enableHorizontalClassMerging));
            });
  }

  @NoVerticalClassMerging
  public interface I {
    @NeverInline
    default void foo() {
      System.out.println("I.foo");
    }
  }

  @NoVerticalClassMerging
  public abstract static class A implements I {}

  @NeverClassInline
  public static class B extends A {

    @Override
    @NeverInline
    public void foo() {
      System.out.println("B.foo");
    }
  }

  @NeverClassInline
  public static class C extends A {}

  public static class Main {

    public static void main(String[] args) {
      callA(args.length == 0 ? new C() : new B());
    }

    @NeverInline
    private static void callA(A a) {
      a.foo();
    }
  }
}
