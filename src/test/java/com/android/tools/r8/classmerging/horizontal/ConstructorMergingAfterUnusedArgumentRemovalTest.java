// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.TestParameters;
import org.junit.Test;

public class ConstructorMergingAfterUnusedArgumentRemovalTest
    extends HorizontalClassMergingTestBase {

  public ConstructorMergingAfterUnusedArgumentRemovalTest(TestParameters parameters) {
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
            inspector ->
                inspector
                    .assertMergedInto(B.class, A.class)
                    .assertMergedInto(C.class, A.class)
                    .assertMergedInto(D.class, A.class))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(
            "A.<init>(?, 43)", "B.<init>(44)", "C.<init>()", "D.<init>()");
  }

  @NeverClassInline
  public static class A {

    public A(int unused, int x) {
      System.out.println("A.<init>(?, " + x + ")");
    }
  }

  @NeverClassInline
  public static class B {

    public B(int x) {
      System.out.println("B.<init>(" + x + ")");
    }
  }

  @NeverClassInline
  public static class C {
    public C() {
      System.out.println("C.<init>()");
    }
  }

  @NeverClassInline
  public static class D {
    public D() {
      System.out.println("D.<init>()");
    }
  }

  public static class Main {
    public static void main(String[] args) {
      new A(42, 43);
      new B(44);
      new C();
      new D();
    }
  }
}
