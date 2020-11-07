// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.notIf;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import org.junit.Test;

public class ClassesWithIdenticalInterfacesTest extends HorizontalClassMergingTestBase {
  public ClassesWithIdenticalInterfacesTest(
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
        .setMinApi(parameters.getApiLevel())
        .addHorizontallyMergedClassesInspector(
            inspector -> inspector.assertMergedInto(Z.class, Y.class))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("bar", "foo y", "foo z")
        .inspect(
            codeInspector -> {
              assertThat(codeInspector.clazz(I.class), isPresent());
              assertThat(codeInspector.clazz(X.class), isPresent());
              assertThat(codeInspector.clazz(Y.class), isPresent());
              assertThat(
                  codeInspector.clazz(Z.class), notIf(isPresent(), enableHorizontalClassMerging));
            });
  }

  public interface I {
    void foo();
  }

  @NeverClassInline
  public static class X {
    @NeverInline
    public void bar() {
      System.out.println("bar");
    }
  }

  @NeverClassInline
  public static class Y implements I {
    @NeverInline
    @Override
    public void foo() {
      System.out.println("foo y");
    }
  }

  @NeverClassInline
  public static class Z implements I {
    @NeverInline
    @Override
    public void foo() {
      System.out.println("foo z");
    }
  }

  public static class Main {
    @NeverInline
    public static void foo(I i) {
      i.foo();
    }

    public static void main(String[] args) {
      X x = new X();
      x.bar();
      Y y = new Y();
      Z z = new Z();
      foo(y);
      foo(z);
    }
  }
}
