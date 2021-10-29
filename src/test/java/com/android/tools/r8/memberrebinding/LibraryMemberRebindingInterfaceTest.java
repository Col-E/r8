// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.memberrebinding;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class LibraryMemberRebindingInterfaceTest extends TestBase {

  private static Path oldRuntimeJar;
  private static Path newRuntimeJar;

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return TestBase.getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @BeforeClass
  public static void compileCustomLib() throws Exception {
    oldRuntimeJar =
        createJar(
            ImmutableList.of(LibraryI.class, LibraryB.class, LibraryC.class),
            getProgramClassFileDataWithoutMethods(LibraryA.class));
    newRuntimeJar =
        createJar(ImmutableList.of(LibraryI.class, LibraryA.class, LibraryB.class, LibraryC.class));
  }

  // Tests old android.jar with old runtime.
  @Test
  public void testEmptyAAtCompileTimeAndRuntime() throws Exception {
    test(oldRuntimeJar, oldRuntimeJar);
  }

  // Tests new android.jar with new runtime.
  @Test
  public void testNonEmptyAAtCompileTimeAndRuntime() throws Exception {
    test(newRuntimeJar, newRuntimeJar);
  }

  // Tests old android.jar with new runtime.
  @Test
  public void testEmptyAAtCompileTime() throws Exception {
    test(oldRuntimeJar, newRuntimeJar);
  }

  // Tests new android.jar with old runtime.
  @Test
  public void testEmptyAAtRuntimeTime() throws Exception {
    test(newRuntimeJar, oldRuntimeJar);
  }

  private void test(Path compileTimeLibrary, Path runtimeLibrary) throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class)
        .addKeepClassAndMembersRules(TestClass.class)
        .addLibraryFiles(compileTimeLibrary)
        .addDefaultRuntimeLibrary(parameters)
        .setMinApi(parameters.getApiLevel())
        .compile()
        .addRunClasspathFiles(buildOnDexRuntime(parameters, runtimeLibrary))
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("42");
  }

  private static Path createJar(Collection<Class<?>> programClasses, byte[]... programClassFileData)
      throws Exception {
    return testForR8(getStaticTemp(), Backend.CF)
        .addProgramClasses(programClasses)
        .addProgramClassFileData(programClassFileData)
        .addKeepAllClassesRule()
        .compile()
        .writeToZip();
  }

  private static byte[] getProgramClassFileDataWithoutMethods(Class<?> clazz) throws IOException {
    return transformer(clazz)
        .removeMethods(
            (int access, String name, String descriptor, String signature, String[] exceptions) ->
                !name.equals("<init>"))
        .transform();
  }

  static class TestClass {

    public static void main(String[] args) {
      test(new LibraryC());
    }

    static void test(LibraryB b) {
      System.out.println(b.m());
    }
  }

  interface LibraryI {

    int m();
  }

  static class LibraryA {

    // Added in API level X, so we can't rebind to this in APIs < X.
    public int m() {
      return 42;
    }
  }

  abstract static class LibraryB extends LibraryA implements LibraryI {}

  static class LibraryC extends LibraryB {

    @Override
    public int m() {
      return 42;
    }
  }
}
