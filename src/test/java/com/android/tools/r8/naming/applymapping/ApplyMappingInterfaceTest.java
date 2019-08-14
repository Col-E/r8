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

@RunWith(Parameterized.class)
public class ApplyMappingInterfaceTest extends TestBase {

  public interface Interface {
    void foo();
  }

  public interface SubInterface extends Interface {}

  public static class InterfaceImpl implements Interface {

    @Override
    public void foo() {
      System.out.println("Hello World!");
    }

    public static void main(String[] args) {
      action(new InterfaceImpl());
    }

    public static void action(Interface perform) {
      perform.foo();
    }
  }

  public static class SubInterfaceImpl implements SubInterface {

    @Override
    public void foo() {
      System.out.println("Hello World!");
    }

    public static void main(String[] args) {
      action(new SubInterfaceImpl());
    }

    public static void action(SubInterface perform) {
      perform.foo();
    }
  }

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().build();
  }

  public ApplyMappingInterfaceTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testDirectInterface()
      throws IOException, CompilationFailedException, ExecutionException {
    test(InterfaceImpl.class, Interface.class);
  }

  @Test
  public void testSubInterface()
      throws IOException, CompilationFailedException, ExecutionException {
    test(SubInterfaceImpl.class, SubInterface.class, Interface.class);
  }

  public void test(Class<?> testClass, Class<?>... interfaces)
      throws IOException, CompilationFailedException, ExecutionException {
    R8TestCompileResult libraryCompileResult =
        testForR8(parameters.getBackend())
            .addProgramClasses(interfaces)
            .addKeepAllClassesRuleWithAllowObfuscation()
            .setMinApi(parameters.getRuntime())
            .compile();
    testForR8(parameters.getBackend())
        .addProgramClasses(testClass)
        .addClasspathClasses(interfaces)
        .addKeepAllClassesRule()
        .addApplyMapping(libraryCompileResult.getProguardMap())
        .setMinApi(parameters.getRuntime())
        .noTreeShaking()
        .compile()
        .addRunClasspathFiles(libraryCompileResult.writeToZip())
        .run(parameters.getRuntime(), testClass)
        .assertSuccessWithOutputLines("Hello World!");
  }
}
