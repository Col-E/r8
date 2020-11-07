// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.codeinspector.HorizontallyMergedClassesInspector;
import org.junit.Test;

public class ClassesWithDifferentInterfacesTest extends HorizontalClassMergingTestBase {
  public ClassesWithDifferentInterfacesTest(
      TestParameters parameters, boolean enableHorizontalClassMerging) {
    super(parameters, enableHorizontalClassMerging);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addOptionsModification(
            options -> options.enableHorizontalClassMerging = enableHorizontalClassMerging)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters.getApiLevel())
        .addHorizontallyMergedClassesInspector(
            HorizontallyMergedClassesInspector::assertNoClassesMerged)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("bar", "foo y", "bar")
        .inspect(
            codeInspector -> {
              assertThat(codeInspector.clazz(I.class), isPresent());
              assertThat(codeInspector.clazz(X.class), isPresent());
              assertThat(codeInspector.clazz(Y.class), isPresent());
              assertThat(codeInspector.clazz(Z.class), isPresent());
            });
  }

  @NoVerticalClassMerging
  public interface I {
    void foo();
  }

  @NoVerticalClassMerging
  public interface J {
    void bar();
  }

  @NeverClassInline
  public static class X {
    @NeverInline
    public void bar() {
      System.out.println("bar");
    }
  }

  @NeverClassInline
  public static class Y extends X implements I {
    @NeverInline
    @Override
    public void foo() {
      System.out.println("foo y");
    }
  }

  @NeverClassInline
  public static class Z extends X implements J {
    @NeverInline
    public void foo() {
      System.out.println("foo z");
    }
  }

  public static class Main {
    @NeverInline
    public static void foo(I i) {
      i.foo();
    }

    @NeverInline
    public static void bar(J j) {
      j.bar();
    }

    public static void main(String[] args) {
      X x = new X();
      x.bar();
      Y y = new Y();
      Z z = new Z();
      foo(y);
      bar(z);
    }
  }
}
