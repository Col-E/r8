// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.applymapping;

import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApplyMappingInterfaceInvokeTest extends TestBase {

  public interface I {
    void foo();
  }

  // Keep this class to test A.foo();
  public abstract static class A implements I {}

  public static class B extends A {

    @Override
    public void foo() {
      System.out.println("Hello World!");
    }
  }

  // Keep this class to test C.foo();
  public static class C extends B {}

  public static class TestApp {

    public static void main(String[] args) {
      testA(new B());
      testC(new C());
    }

    public static void testA(A a) {
      a.foo();
    }

    public static void testC(C c) {
      c.foo();
    }
  }

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ApplyMappingInterfaceInvokeTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testInvokeVirtual() throws Exception {
    Class<?>[] classPathClasses = {I.class, A.class, B.class, C.class};
    R8TestCompileResult libraryResult =
        testForR8(parameters.getBackend())
            .addProgramClasses(classPathClasses)
            .addKeepAllClassesRuleWithAllowObfuscation()
            .setMinApi(parameters)
            .compile();
    testForR8(parameters.getBackend())
        .addClasspathClasses(classPathClasses)
        .addProgramClasses(TestApp.class)
        .addDontObfuscate()
        .noTreeShaking()
        .addApplyMapping(libraryResult.getProguardMap())
        .setMinApi(parameters)
        .compile()
        .addRunClasspathFiles(libraryResult.writeToZip())
        .run(parameters.getRuntime(), TestApp.class)
        .assertSuccessWithOutputLines("Hello World!", "Hello World!");
  }
}
