// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal.dispatch;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNot.not;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.classmerging.horizontal.HorizontalClassMergingTestBase;
import org.junit.Test;

public class SuperMethodMergedTest extends HorizontalClassMergingTestBase {
  public SuperMethodMergedTest(TestParameters parameters, boolean enableHorizontalClassMerging) {
    super(parameters, enableHorizontalClassMerging);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(this.getClass())
        .addKeepMainRule(Main.class)
        .addOptionsModification(
            options -> options.enableHorizontalClassMerging = enableHorizontalClassMerging)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("foo", "parent b", "parent b", "x", "parent b")
        .inspect(
            codeInspector -> {
              if (enableHorizontalClassMerging) {
                assertThat(codeInspector.clazz(ParentA.class), isPresent());
                assertThat(codeInspector.clazz(ParentB.class), not(isPresent()));
                assertThat(codeInspector.clazz(X.class), isPresent());
                assertThat(codeInspector.clazz(Y.class), not(isPresent()));
                // TODO(b/165517236): Explicitly check classes have been merged.
              } else {
                assertThat(codeInspector.clazz(ParentA.class), isPresent());
                assertThat(codeInspector.clazz(ParentB.class), isPresent());
                assertThat(codeInspector.clazz(X.class), isPresent());
                assertThat(codeInspector.clazz(Y.class), isPresent());
              }
            });
  }

  @NeverClassInline
  public static class ParentA {
    @NeverInline
    void foo() {
      System.out.println("foo");
    }
  }

  @NoVerticalClassMerging
  @NeverClassInline
  public static class ParentB {
    @NeverInline
    void print() {
      System.out.println("parent b");
    }
  }

  @NeverClassInline
  public static class X extends ParentB {
    public X() {
      print();
    }

    @NeverInline
    @Override
    void print() {
      super.print();
      System.out.println("x");
    }
  }

  @NeverClassInline
  public static class Y extends ParentB {
    public Y() {
      print();
    }
  }

  public static class Main {
    public static void main(String[] args) {
      ParentA a = new ParentA();
      a.foo();
      ParentB b = new ParentB();
      b.print();
      X x = new X();
      Y y = new Y();
    }
  }
}
