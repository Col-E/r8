// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.applymapping;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

// This is a reproduction of b/144151805.
@RunWith(Parameterized.class)
public class ApplyMappingExtendEmptyInterfaceTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ApplyMappingExtendEmptyInterfaceTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testRuntime() throws ExecutionException, CompilationFailedException, IOException {
    testForRuntime(parameters)
        .addProgramClasses(
            TestI.class,
            TestA.class,
            Main.class,
            LibI.class,
            LibI2.class,
            LibI3.class,
            Runner.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("injectTestA", "injectObject");
  }

  @Test
  public void testInheritLibraryInterface()
      throws CompilationFailedException, IOException, ExecutionException {
    final R8TestCompileResult libCompileResult =
        testForR8(parameters.getBackend())
            .addProgramClasses(LibI.class, LibI2.class, LibI3.class, Runner.class)
            .addKeepClassAndMembersRules(Runner.class)
            .addKeepClassAndMembersRulesWithAllowObfuscation(LibI.class, LibI2.class, LibI3.class)
            .setMinApi(parameters.getApiLevel())
            .compile();
    testForR8(parameters.getBackend())
        .addProgramClasses(TestI.class, TestA.class, Main.class)
        .addClasspathClasses(LibI.class, LibI2.class, LibI3.class, Runner.class)
        .addKeepAllClassesRule()
        .addApplyMapping(libCompileResult.getProguardMap())
        .addRunClasspathFiles(libCompileResult.writeToZip())
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("injectTestA", "injectObject");
  }

  public interface LibI {
    void inject(Object object);
  }

  public interface LibI2 extends LibI {}

  // Add an additional interface on top of LibI2 to ensure a class naming is generated here.
  public interface LibI3 extends LibI2 {
    void foo();
  }

  public static class Runner {

    public static void foo(LibI libI) {
      libI.inject(libI);
    }
  }

  public interface TestI extends LibI3 {
    void inject(TestA testA);
  }

  public static class TestA implements TestI {

    @Override
    public void inject(Object object) {
      System.out.println("injectObject");
    }

    @Override
    public void inject(TestA testA) {
      System.out.println("injectTestA");
    }

    @Override
    public void foo() {
      throw new RuntimeException("Should never be called");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      final TestA testA = new TestA();
      testA.inject(testA);
      Runner.foo(testA);
    }
  }
}
