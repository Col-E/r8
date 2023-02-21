// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.methods.interfaces;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static junit.framework.TestCase.assertEquals;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNot.not;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InterfaceDefaultMethodKeptTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withAllRuntimes()
        .withApiLevelsStartingAtIncluding(AndroidApiLevel.N)
        .build();
  }

  public InterfaceDefaultMethodKeptTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testKeepingAFooAndIFoo()
      throws CompilationFailedException, IOException, ExecutionException {
    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addProgramClasses(I.class, A.class)
            .setMinApi(parameters)
            .addKeepClassAndMembersRules(I.class)
            .addKeepMethodRules(A.class, "void <init>()", "void foo()")
            .compile()
            .inspect(
                codeInspector -> {
                  assertThat(codeInspector.clazz(A.class), isPresent());
                  assertThat(
                      codeInspector.clazz(A.class).uniqueMethodWithOriginalName("foo"),
                      not(isPresent()));
                });
    testForRuntime(parameters)
        .addProgramClasses(Main.class)
        .addRunClasspathFiles(compileResult.writeToZip())
        .run(parameters.getRuntime(), Main.class, A.class.getTypeName(), "foo")
        .assertSuccessWithOutputLines("Hello World!");
  }

  @Test
  public void testKeepingBFooAndIFoo()
      throws CompilationFailedException, IOException, ExecutionException {
    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addProgramClasses(I.class, A.class, B.class)
            .setMinApi(parameters)
            .addKeepClassAndMembersRules(I.class)
            .addKeepMethodRules(B.class, "void <init>()", "void foo()")
            .compile()
            .inspect(
                codeInspector -> {
                  assertThat(codeInspector.clazz(A.class), not(isPresent()));
                  assertThat(codeInspector.clazz(B.class), isPresent());
                  assertThat(
                      codeInspector.clazz(B.class).uniqueMethodWithOriginalName("foo"),
                      not(isPresent()));
                });
    testForRuntime(parameters)
        .addProgramClasses(Main.class)
        .addRunClasspathFiles(compileResult.writeToZip())
        .run(parameters.getRuntime(), Main.class, B.class.getTypeName(), "foo")
        .assertSuccessWithOutputLines("Hello World!");
  }

  @Test
  public void testKeepingBFooAndAFoo()
      throws CompilationFailedException, IOException, ExecutionException {
    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addProgramClasses(I.class, A.class, B.class)
            .setMinApi(parameters)
            .addKeepMethodRules(A.class, "void <init>()", "void foo()")
            .addKeepMethodRules(B.class, "void <init>()", "void foo()")
            .addDontObfuscate()
            .compile()
            .inspect(
                codeInspector -> {
                  assertThat(codeInspector.clazz(A.class), isPresent());
                  assertThat(
                      codeInspector.clazz(A.class).uniqueMethodWithOriginalName("foo"),
                      isPresent());
                  assertThat(codeInspector.clazz(B.class), isPresent());
                  // TODO(b/144409021): We should be able to remove this.
                  assertEquals(
                      parameters.isDexRuntime(),
                      codeInspector.clazz(B.class).uniqueMethodWithOriginalName("foo").isPresent());
                });
    testForRuntime(parameters)
        .addProgramClasses(Main.class)
        .addRunClasspathFiles(compileResult.writeToZip())
        .run(parameters.getRuntime(), Main.class, B.class.getTypeName(), "foo")
        .assertSuccessWithOutputLines("Hello World!");
  }

  interface I {

    default void foo() {
      System.out.println("Hello World!");
    }
  }

  public static class A implements I {}

  public static class B extends A {}

  public static class Main {

    public static void main(String[] args) throws Exception {
      Object o = Class.forName(args[0]).getDeclaredConstructor().newInstance();
      o.getClass().getMethod(args[1]).invoke(o);
    }
  }
}
