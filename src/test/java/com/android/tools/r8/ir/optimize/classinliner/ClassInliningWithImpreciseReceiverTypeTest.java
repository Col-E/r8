// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ClassInliningWithImpreciseReceiverTypeTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ClassInliningWithImpreciseReceiverTypeTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(ClassInliningWithImpreciseReceiverTypeTest.class)
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("C1", "C2");
  }

  static class TestClass {

    public static void main(String[] args) {
      // After force inlining asB() and build(), the class inliner will attempt to force inline
      // internalBuild(), but the receiver of that invoke is defined by a check-cast instruction
      // that casts the receiver to B.
      System.out.println(new C1().asB().build());
      System.out.println(new C2().asB().build());
    }
  }

  @NoVerticalClassMerging
  abstract static class A {

    @NeverInline
    B asB() {
      return (B) this;
    }

    @NeverInline
    String build() {
      return internalBuild();
    }

    abstract String internalBuild();
  }

  abstract static class B extends A {}

  static class C1 extends B {

    @NeverInline
    String internalBuild() {
      return System.currentTimeMillis() > 0 ? "C1" : null;
    }
  }

  static class C2 extends B {

    @NeverInline
    String internalBuild() {
      return System.currentTimeMillis() > 0 ? "C2" : null;
    }
  }
}
