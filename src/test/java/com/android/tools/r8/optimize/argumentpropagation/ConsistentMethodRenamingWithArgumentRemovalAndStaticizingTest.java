// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** Reproduction of b/241426917. */
@RunWith(Parameterized.class)
public class ConsistentMethodRenamingWithArgumentRemovalAndStaticizingTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A.m()", "B.m()");
  }

  static class Main {

    public static void main(String[] args) {
      String used = System.currentTimeMillis() > 0 ? "A.m()" : null;
      new A().m(used);

      String unused = null;
      B.m(unused);
    }
  }

  // To force A and B into the same strongly connected component.
  static class Parent {}

  @NeverClassInline
  @NoHorizontalClassMerging
  static class A extends Parent {

    // Will have its receiver argument removed (i.e., the method is staticized).
    @NeverInline
    void m(String used) {
      System.out.println(used);
    }
  }

  @NoHorizontalClassMerging
  static class B extends Parent {

    // Will have its first argument removed.
    @NeverInline
    static void m(String unused) {
      System.out.println("B.m()");
    }
  }
}
