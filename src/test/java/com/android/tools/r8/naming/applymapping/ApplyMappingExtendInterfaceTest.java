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
public class ApplyMappingExtendInterfaceTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ApplyMappingExtendInterfaceTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testRuntime() throws ExecutionException, CompilationFailedException, IOException {
    testForRuntime(parameters)
        .addProgramClasses(TestI.class, TestA.class, Main.class, LibI.class, Runner.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("injectTestA", "injectObject");
  }

  @Test
  public void testInheritLibraryInterface()
      throws CompilationFailedException, IOException, ExecutionException {
    final R8TestCompileResult libCompileResult =
        testForR8(parameters.getBackend())
            .addProgramClasses(LibI.class, Runner.class)
            .addKeepClassAndMembersRules(Runner.class)
            .addKeepClassAndMembersRulesWithAllowObfuscation(LibI.class)
            .setMinApi(parameters)
            .compile();
    testForR8(parameters.getBackend())
        .addProgramClasses(TestI.class, TestA.class, Main.class)
        .addClasspathClasses(LibI.class, Runner.class)
        .addKeepAllClassesRule()
        .addApplyMapping(libCompileResult.getProguardMap())
        .addRunClasspathFiles(libCompileResult.writeToZip())
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("injectTestA", "injectObject");
  }

  public interface LibI {
    void inject(Object object);
  }

  public static class Runner {

    public static void foo(LibI libI) {
      libI.inject(libI);
    }
  }

  public interface TestI extends LibI {
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
  }

  public static class Main {

    public static void main(String[] args) {
      final TestA testA = new TestA();
      testA.inject(testA);
      Runner.foo(testA);
    }
  }
}
