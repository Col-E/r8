// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.memberrebinding;

import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class LibraryMemberRebindingTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return TestBase.getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public LibraryMemberRebindingTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testWithEmptyA() throws Exception {
    Path compileTimeLibrary = compileLibraryForAppCompilation(LibraryB.class, LibraryA.class);
    test(compileTimeLibrary, compileTimeLibrary);
  }

  @Test
  public void testWithEmptyB() throws Exception {
    Path compileTimeLibrary = compileLibraryForAppCompilation(LibraryA.class, LibraryB.class);
    test(compileTimeLibrary, compileTimeLibrary);
  }

  @Test
  public void testWithEmptyBOnlyAtCompileTime() throws Exception {
    Path compileTimeLibrary = compileLibraryForAppCompilation(LibraryA.class, LibraryB.class);
    Path runtimeLibrary = compileLibraryForAppCompilation(LibraryB.class, LibraryA.class);
    test(compileTimeLibrary, runtimeLibrary);
  }

  private void test(Path compileTimeLibrary, Path runtimeLibrary) throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class)
        .addKeepMainRule(TestClass.class)
        .addLibraryFiles(compileTimeLibrary)
        .addDefaultRuntimeLibrary(parameters)
        .setMinApi(parameters)
        .compile()
        .apply(compileResult -> configureRunClasspath(compileResult, runtimeLibrary))
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("42");
  }

  private Path compileLibraryForAppCompilation(
      Class<?> nonEmptyLibraryClass, Class<?> emptyLibraryClass) throws Exception {
    return testForR8(Backend.CF)
        .addProgramClasses(nonEmptyLibraryClass)
        .addProgramClassFileData(
            transformer(emptyLibraryClass)
                .removeFields(
                    (int access, String name, String descriptor, String signature, Object value) ->
                        true)
                .removeMethods(
                    (int access,
                        String name,
                        String descriptor,
                        String signature,
                        String[] exceptions) -> !name.equals("<init>"))
                .transform())
        .addKeepAllClassesRule()
        .compile()
        .writeToZip();
  }

  private void configureRunClasspath(R8TestCompileResult compileResult, Path library)
      throws Exception {
    if (parameters.isCfRuntime()) {
      compileResult.addRunClasspathFiles(library);
    } else {
      compileResult.addRunClasspathFiles(
          testForD8().addProgramFiles(library).setMinApi(parameters).compile().writeToZip());
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(LibraryB.f + LibraryB.m());
    }
  }

  static class LibraryA {

    public static int f = 21;

    public static int m() {
      return 21;
    }
  }

  static class LibraryB extends LibraryA {

    public static int f = 21;

    public static int m() {
      return 21;
    }
  }
}
