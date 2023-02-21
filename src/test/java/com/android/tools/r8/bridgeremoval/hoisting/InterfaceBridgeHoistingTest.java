// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.bridgeremoval.hoisting;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class InterfaceBridgeHoistingTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public InterfaceBridgeHoistingTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class, A.class, I.class)
        .addProgramClassFileData(
            transformer(B.class)
                .setBridge(B.class.getDeclaredMethod("bridge", Object.class))
                .transform())
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(new B().bridge("Hello world!"));
    }
  }

  @NoVerticalClassMerging
  static class A {}

  @NoVerticalClassMerging
  interface I {

    @NeverInline
    default Object m(String arg) {
      return System.currentTimeMillis() >= 0 ? arg : null;
    }
  }

  @NeverClassInline
  static class B extends A implements I {

    // Hoisting this bridge to class A would lead to a NoSuchMethodError since the targeted method
    // is defined on the interface I.
    @NeverInline
    public /*bridge*/ String bridge(Object o) {
      return (String) m((String) o);
    }
  }
}
