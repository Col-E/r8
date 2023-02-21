// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.methods.interfaces;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InterfaceMethodKeepResolutionTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public InterfaceMethodKeepResolutionTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testKeepKShouldKeepK()
      throws CompilationFailedException, IOException, ExecutionException {
    runTest(
        ImmutableList.of(I.class, J.class, K.class),
        K.class,
        ImmutableList.of(ImplK.class),
        ImplK.class);
  }

  @Test
  public void testKeepLShouldKeepIorL()
      throws CompilationFailedException, IOException, ExecutionException {
    runTest(
        ImmutableList.of(I.class, J.class, L.class),
        L.class,
        ImmutableList.of(ImplL.class),
        ImplL.class);
  }

  @Test
  public void testKeepClassShouldKeepA()
      throws CompilationFailedException, IOException, ExecutionException {
    runTest(
        ImmutableList.of(I.class, J.class, K.class, A.class),
        A.class,
        ImmutableList.of(B.class, Main.class),
        Main.class);
  }

  private void runTest(
      Collection<Class<?>> classPathClasses,
      Class<?> libraryClassWithMethod,
      Collection<Class<?>> programClasses,
      Class<?> main)
      throws CompilationFailedException, IOException, ExecutionException {
    R8FullTestBuilder libraryBuilder =
        testForR8(parameters.getBackend())
            .addProgramClasses(classPathClasses)
            .setMinApi(parameters)
            .addKeepMethodRules(libraryClassWithMethod, "void foo()");
    if (!libraryClassWithMethod.isInterface()) {
      libraryBuilder.addKeepClassRules(libraryClassWithMethod);
    }
    testForRuntime(parameters)
        .addProgramClasses(programClasses)
        .addRunClasspathFiles(libraryBuilder.compile().writeToZip())
        .run(parameters.getRuntime(), main)
        .assertSuccessWithOutputLines("Hello World!");
  }

  public interface I {
    void foo();
  }

  public interface J extends I {
    void foo();
  }

  public interface K extends J {}

  public interface L extends I, J {}

  public abstract static class A implements K {}

  public static class ImplK implements K {

    @Override
    public void foo() {
      System.out.println("Hello World!");
    }

    public static void main(String[] args) {
      ((K) new ImplK()).foo();
    }
  }

  public static class ImplL implements L {

    @Override
    public void foo() {
      System.out.println("Hello World!");
    }

    public static void main(String[] args) {
      ((L) new ImplL()).foo();
    }
  }

  public static class B extends A {

    @Override
    public void foo() {
      System.out.println("Hello World!");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      ((A) new B()).foo();
    }
  }
}
