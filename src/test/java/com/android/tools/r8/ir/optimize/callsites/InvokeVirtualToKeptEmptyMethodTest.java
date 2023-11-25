// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.callsites;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethod;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InvokeVirtualToKeptEmptyMethodTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    R8TestCompileResult appCompileResult =
        testForR8(parameters.getBackend())
            .addProgramClasses(Main.class, I.class, A.class, Runner.class)
            .addKeepMainRule(Main.class)
            .addKeepClassAndMembersRules(I.class, Runner.class)
            .setMinApi(parameters)
            .compile()
            .inspect(
                inspector -> {
                  ClassSubject iClassSubject = inspector.clazz(I.class);
                  assertThat(iClassSubject, isPresent());

                  MethodSubject fooMethodSubject =
                      iClassSubject.uniqueMethodWithOriginalName("foo");
                  assertThat(fooMethodSubject, isPresent());

                  ClassSubject runnerClassSubject = inspector.clazz(Runner.class);
                  assertThat(runnerClassSubject, isPresent());

                  MethodSubject callFooMethodSubject =
                      runnerClassSubject.uniqueMethodWithOriginalName("callFoo");
                  assertThat(callFooMethodSubject, isPresent());
                  assertThat(callFooMethodSubject, invokesMethod(fooMethodSubject));
                });

    appCompileResult.run(parameters.getRuntime(), Main.class).assertSuccessWithEmptyOutput();

    testForR8(parameters.getBackend())
        .addProgramClasses(TestMain.class, B.class)
        .addClasspathClasses(I.class, Runner.class)
        .addKeepAllClassesRule()
        .setMinApi(parameters)
        .compile()
        .addRunClasspathFiles(appCompileResult.writeToZip())
        .run(parameters.getRuntime(), TestMain.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  static class Main {

    public static void main(String[] args) {
      Runner.callFoo(new A());
    }
  }

  static class TestMain {

    public static void main(String[] args) {
      Runner.callFoo(new B());
    }
  }

  public static class Runner {

    public static void callFoo(I i) {
      i.foo();
    }
  }

  // @Keep
  public interface I {

    void foo();
  }

  static class A implements I {

    @Override
    public void foo() {
      // Intentionally empty.
    }
  }

  static class B implements I {

    @Override
    public void foo() {
      System.out.println("Hello, world!");
    }
  }
}
