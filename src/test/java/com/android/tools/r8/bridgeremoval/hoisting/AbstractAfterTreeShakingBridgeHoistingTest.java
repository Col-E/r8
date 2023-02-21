// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.bridgeremoval.hoisting;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Regression test for b/195037294. */
@RunWith(Parameterized.class)
public class AbstractAfterTreeShakingBridgeHoistingTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public AbstractAfterTreeShakingBridgeHoistingTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class, A.class)
        .addProgramClassFileData(
            transformer(B1.class)
                .setBridge(B1.class.getDeclaredMethod("virtualBridge", Object.class))
                .transform(),
            transformer(B2.class)
                .setBridge(B2.class.getDeclaredMethod("virtualBridge", Object.class))
                .transform())
        .addKeepMainRule(TestClass.class)
        // Keep test() method to disable call site optimization for that method.
        .addKeepRules(
            "-keepclassmembers class " + TestClass.class.getTypeName() + " {",
            "  void test(...);",
            "}")
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput("Hello");
  }

  static class TestClass {

    public static void main(String[] args) {
      B1 b1 = null;
      B2 b2 = null;
      // Dead instantiations of B1 and B2. This way, R8 considers B1 and B2 to be instantiated until
      // the second round of tree shaking. After the second round of tree shaking, the
      // virtualBridge() methods on B1 and B2 are made abstract, since they are not instantiated.
      // This should disable bridge hoisting.
      if (alwaysFalse()) {
        b1 = B1.create();
        b2 = B2.create();
      }
      test(new A(), b1, b2);
    }

    static boolean alwaysFalse() {
      return false;
    }

    @NeverInline
    private static void test(A a, B1 b1, B2 b2) {
      System.out.print(a.m("Hello"));
      if (b1 != null) {
        System.out.print(b1.virtualBridge(" "));
      }
      if (b2 != null) {
        System.out.println(b2.virtualBridge("world!"));
      }
    }
  }

  static class A {

    @NeverInline
    public Object m(String arg) {
      return System.currentTimeMillis() >= 0 ? arg : null;
    }
  }

  @NeverClassInline
  static class B1 extends A {

    static B1 create() {
      return new B1();
    }

    @NeverInline
    public /*bridge*/ String virtualBridge(Object o) {
      return (String) m((String) o);
    }
  }

  @NeverClassInline
  static class B2 extends A {

    static B2 create() {
      return new B2();
    }

    @NeverInline
    public /*bridge*/ String virtualBridge(Object o) {
      return (String) m((String) o);
    }
  }
}
