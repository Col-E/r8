// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.workaround;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbstract;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class InputWithAbstractMethodOnNonAbstractClassTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public InputWithAbstractMethodOnNonAbstractClassTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8()
        .addProgramClasses(TestClass.class)
        .addProgramClassFileData(transformer(Greeter.class).unsetAbstract().transform())
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class)
        .addProgramClassFileData(transformer(Greeter.class).unsetAbstract().transform())
        .addKeepMainRule(TestClass.class)
        .addKeepClassAndMembersRules(Greeter.class)
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  @Test
  public void testJVM() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClasses(TestClass.class)
        .addProgramClassFileData(transformer(Greeter.class).unsetAbstract().transform())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  private void inspect(CodeInspector inspector) {
    MethodSubject methodOfInterest =
        inspector.clazz(Greeter.class).uniqueMethodWithOriginalName("dead");
    assertThat(methodOfInterest, isPresent());
    if (parameters.isCfRuntime() || parameters.getApiLevel().isLessThan(AndroidApiLevel.L)) {
      assertThat(methodOfInterest, not(isAbstract()));
    } else {
      assertThat(methodOfInterest, isAbstract());
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      Greeter.greet();
    }
  }

  /*not-*/ abstract static class Greeter {

    static void greet() {
      System.out.println("Hello world!");
    }

    abstract void dead();
  }
}
