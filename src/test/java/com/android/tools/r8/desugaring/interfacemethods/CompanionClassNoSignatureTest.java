// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugaring.interfacemethods;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.GenericSignature.ClassSignature;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class CompanionClassNoSignatureTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("I.foo");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().enableApiLevelsForCf().build();
  }

  public CompanionClassNoSignatureTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters.getBackend())
        .setMinApi(parameters)
        .addProgramClasses(I.class, A.class, TestClass.class)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED)
        .inspect(
            inspector -> {
              if (parameters
                  .getApiLevel()
                  .isLessThan(apiLevelWithDefaultInterfaceMethodsSupport())) {
                ClassSignature signature =
                    inspector.companionClassFor(I.class).getDexProgramClass().getClassSignature();
                assertTrue(
                    "Expected no signature, got: " + signature.toString(),
                    signature.hasNoSignature());
              }
            });
  }

  interface I {
    default void foo() {
      System.out.println("I.foo");
    }
  }

  static class A implements I {
    // no override of default.
  }

  static class TestClass {

    public static void main(String[] args) {
      new A().foo();
    }
  }
}
