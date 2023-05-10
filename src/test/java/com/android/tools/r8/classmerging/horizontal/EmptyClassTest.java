// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.notIf;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.TestParameters;
import org.junit.Test;

public class EmptyClassTest extends HorizontalClassMergingTestBase {
  public EmptyClassTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters)
        .addHorizontallyMergedClassesInspector(
            inspector -> inspector.assertMergedInto(B.class, A.class))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("a", "b: foo")
        .inspect(
            codeInspector -> {
              assertThat(
                  codeInspector.clazz(A.class),
                  notIf(isPresent(), parameters.canInitNewInstanceUsingSuperclassConstructor()));
              assertThat(codeInspector.clazz(B.class), isAbsent());
            });
  }

  @NeverClassInline
  public static class A {
    public A() {
      System.out.println("a");
    }
  }

  @NeverClassInline
  public static class B {
    public B(String s) {
      System.out.println("b: " + s);
    }
  }

  public static class Main {
    public static void main(String[] args) {
      A a = new A();
      B b = new B("foo");
    }
  }
}
