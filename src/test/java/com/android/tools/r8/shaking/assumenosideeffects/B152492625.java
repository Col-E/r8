// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.assumenosideeffects;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class B152492625 extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public B152492625(TestParameters parameters) {
    this.parameters = parameters;
  }

  private void noCallToWait(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(TestClass.class);
    assertThat(classSubject, isPresent());
    classSubject.forAllMethods(
        foundMethodSubject ->
            foundMethodSubject
                .instructions(InstructionSubject::isInvokeVirtual)
                .forEach(
                    instructionSubject -> {
                      Assert.assertNotEquals(
                          "wait", instructionSubject.getMethod().name.toString());
                    }));
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(B152492625.class)
        .addKeepMainRule(TestClass.class)
        .addKeepRules("-assumenosideeffects class " + B.class.getTypeName() + " { *; }")
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(this::noCallToWait)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello, world");
  }

  @Test
  public void testProguardNotRemovingWait() throws Exception {
    Assume.assumeTrue(parameters.isCfRuntime());

    testForProguard()
        .addInnerClasses(B152492625.class)
        .addKeepMainRule(TestClass.class)
        .addKeepRules("-assumenosideeffects class " + B.class.getTypeName() + " { *; }")
        .addKeepRules("-dontwarn " + B152492625.class.getTypeName())
        .setMinApi(parameters.getApiLevel())
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertFailureWithErrorThatThrows(IllegalMonitorStateException.class);
  }

  @Test
  public void testProguardRemovingWait() throws Exception {
    Assume.assumeTrue(parameters.isCfRuntime());

    testForProguard()
        .addInnerClasses(B152492625.class)
        .addKeepMainRule(TestClass.class)
        .addKeepRules("-assumenosideeffects class java.lang.Object { void wait(); }")
        .addKeepRules("-dontwarn " + B152492625.class.getTypeName())
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(this::noCallToWait)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello, world");
  }

  static class TestClass {

    public void m() throws Exception {
      System.out.println("Hello, world");
      // test fails if wait is not removed.
      wait();
    }

    public static void main(String[] args) throws Exception {
      new TestClass().m();
    }
  }

  static class B {}
}
