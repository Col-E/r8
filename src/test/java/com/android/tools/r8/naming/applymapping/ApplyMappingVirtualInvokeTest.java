// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.applymapping;

import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.naming.applymapping.ApplyMappingVirtualInvokeTest.TestClasses.LibraryBase;
import com.android.tools.r8.naming.applymapping.ApplyMappingVirtualInvokeTest.TestClasses.LibrarySubclass;
import com.android.tools.r8.naming.applymapping.ApplyMappingVirtualInvokeTest.TestClasses.ProgramClass;
import com.android.tools.r8.naming.applymapping.ApplyMappingVirtualInvokeTest.TestClasses.ProgramSubclass;
import com.android.tools.r8.utils.StringUtils;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ApplyMappingVirtualInvokeTest extends TestBase {

  public static final String EXPECTED_PROGRAM = StringUtils.lines("LibraryBase.foo");
  public static final String EXPECTED_PROGRAM_SUBCLASS =
      StringUtils.lines("ProgramSubclass.foo", "LibraryBase.foo");

  public static class TestClasses {

    public static class LibraryBase {

      void foo() {
        System.out.println("LibraryBase.foo");
      }
    }

    public static class LibrarySubclass extends LibraryBase {}

    public static class ProgramClass {

      public static void main(String[] args) {
        new LibrarySubclass().foo();
      }
    }

    public static class ProgramSubclass extends LibrarySubclass {

      public static void main(String[] args) {
        new ProgramSubclass().foo();
      }

      @Override
      void foo() {
        System.out.println("ProgramSubclass.foo");
        super.foo();
      }
    }
  }

  private static final Class<?>[] LIBRARY_CLASSES = {LibraryBase.class, LibrarySubclass.class};
  private static final Class<?>[] PROGRAM_CLASSES = {ProgramClass.class, ProgramSubclass.class};
  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().build();
  }

  public ApplyMappingVirtualInvokeTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private static Function<Backend, R8TestCompileResult> compilationResults =
      memoizeFunction(ApplyMappingVirtualInvokeTest::compile);

  private static R8TestCompileResult compile(Backend backend) throws CompilationFailedException {
    return testForR8(getStaticTemp(), backend)
        .addProgramClasses(LIBRARY_CLASSES)
        .addKeepClassAndMembersRulesWithAllowObfuscation(LIBRARY_CLASSES)
        .addOptionsModification(
            options -> {
              options.enableInlining = false;
              options.enableVerticalClassMerging = false;
              options.enableHorizontalClassMerging = false;
              options.enableClassInlining = false;
            })
        .compile();
  }

  @Test
  public void runJvmProgramTest()
      throws ExecutionException, CompilationFailedException, IOException {
    assumeTrue(parameters.isCfRuntime());
    testForJvm()
        .addProgramClasses(LIBRARY_CLASSES)
        .addProgramClasses(PROGRAM_CLASSES)
        .run(parameters.getRuntime(), ProgramClass.class)
        .assertSuccessWithOutput(EXPECTED_PROGRAM);
  }

  @Test
  public void runJvmProgramSubclassTest()
      throws ExecutionException, CompilationFailedException, IOException {
    assumeTrue(parameters.isCfRuntime());
    testForJvm()
        .addProgramClasses(LIBRARY_CLASSES)
        .addProgramClasses(PROGRAM_CLASSES)
        .run(parameters.getRuntime(), ProgramSubclass.class)
        .assertSuccessWithOutput(EXPECTED_PROGRAM_SUBCLASS);
  }

  @Ignore("b/128868424")
  @Test
  public void testProgramClass()
      throws ExecutionException, CompilationFailedException, IOException {
    runTest(ProgramClass.class, EXPECTED_PROGRAM);
  }

  @Ignore("b/128868424")
  @Test
  public void testProgramSubClass()
      throws ExecutionException, IOException, CompilationFailedException {
    runTest(ProgramSubclass.class, EXPECTED_PROGRAM_SUBCLASS);
  }

  private void runTest(Class<?> main, String expected)
      throws ExecutionException, IOException, CompilationFailedException {
    R8TestCompileResult libraryCompileResult = compilationResults.apply(parameters.getBackend());
    Path outPath = temp.newFile("out.zip").toPath();
    libraryCompileResult.writeToZip(outPath);
    testForR8(parameters.getBackend())
        .noTreeShaking()
        .noMinification()
        .addProgramClasses(PROGRAM_CLASSES)
        .addApplyMapping(libraryCompileResult.getProguardMap())
        .addLibraryClasses(LIBRARY_CLASSES)
        .addLibraryFiles(runtimeJar(parameters.getBackend()))
        .setMinApi(parameters.getRuntime())
        .compile()
        .addRunClasspathFiles(outPath)
        .run(parameters.getRuntime(), main)
        .assertSuccessWithOutput(expected);
  }
}
