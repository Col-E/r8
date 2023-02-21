// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.methods.interfaces;

import static com.android.tools.r8.references.Reference.classFromClass;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MultipleRulesRegression228791247Test extends TestBase {

  private static final String EXPECTED = StringUtils.lines("Hello!");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public MultipleRulesRegression228791247Test(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testReference() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(I.class, J.class, A.class, TestClass.class)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    // Regression adds two rules causes the forwarding method to be generated twice.
    String rule1 = "-keep class " + classFromClass(J.class).getTypeName() + "{ void *oo(); }";
    String rule2 = "-keep class " + classFromClass(J.class).getTypeName() + "{ void fo*(); }";
    testForR8(parameters.getBackend())
        .addProgramClasses(I.class, J.class, A.class, TestClass.class)
        .addKeepMainRule(TestClass.class)
        .addKeepRules(rule1, rule2)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  public interface I {
    default void foo() {
      if (System.nanoTime() > 0) {
        System.out.println("Hello!");
      }
    }
  }

  public interface J extends I {
    // No foo, but it will be kept at J::foo.
  }

  public static class A implements J {}

  public static class TestClass {

    public static void main(String[] args) {
      J j = System.nanoTime() > 0 ? new A() : null;
      j.foo();
    }
  }
}
