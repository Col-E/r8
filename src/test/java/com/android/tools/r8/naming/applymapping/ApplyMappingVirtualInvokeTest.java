// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.applymapping;


import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.naming.applymapping.ApplyMappingVirtualInvokeTest.TestClasses.LibraryBase;
import com.android.tools.r8.naming.applymapping.ApplyMappingVirtualInvokeTest.TestClasses.LibraryInterface;
import com.android.tools.r8.naming.applymapping.ApplyMappingVirtualInvokeTest.TestClasses.LibrarySubclass;
import com.android.tools.r8.naming.applymapping.ApplyMappingVirtualInvokeTest.TestClasses.ProgramClass;
import com.android.tools.r8.naming.applymapping.ApplyMappingVirtualInvokeTest.TestClasses.ProgramSubclass;
import com.android.tools.r8.utils.StringUtils;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApplyMappingVirtualInvokeTest extends TestBase {

  public static final String EXPECTED_PROGRAM =
      StringUtils.lines("LibraryBase.foo", "LibraryBase.bar");
  public static final String EXPECTED_PROGRAM_SUBCLASS =
      StringUtils.lines(
          "ProgramSubclass.foo", "LibraryBase.foo", "ProgramSubclass.bar", "LibraryBase.bar");

  public static class TestClasses {

    public interface LibraryInterface {
      void bar();
    }

    public static class LibraryBase implements LibraryInterface {

      public void foo() {
        System.out.println("LibraryBase.foo");
      }

      @Override
      public void bar() {
        System.out.println("LibraryBase.bar");
      }
    }

    public static class LibrarySubclass extends LibraryBase {}

    public static class ProgramClass {

      public static void main(String[] args) {
        new LibrarySubclass().foo();
        new LibrarySubclass().bar();
      }
    }

    public static class ProgramSubclass extends LibrarySubclass {

      public static void main(String[] args) {
        new ProgramSubclass().foo();
        new ProgramSubclass().bar();
      }

      @Override
      public void foo() {
        System.out.println("ProgramSubclass.foo");
        super.foo();
      }

      @Override
      public void bar() {
        System.out.println("ProgramSubclass.bar");
        super.bar();
      }
    }
  }

  private static final Class<?>[] LIBRARY_CLASSES = {
    LibraryInterface.class, LibraryBase.class, LibrarySubclass.class
  };
  private static final Class<?>[] PROGRAM_CLASSES = {ProgramClass.class, ProgramSubclass.class};

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private static Function<TestParameters, R8TestCompileResult> compilationResults =
      memoizeFunction(ApplyMappingVirtualInvokeTest::compile);

  private static R8TestCompileResult compile(TestParameters parameters)
      throws CompilationFailedException {
    return testForR8(getStaticTemp(), parameters.getBackend())
        .addProgramClasses(LIBRARY_CLASSES)
        .addKeepClassAndMembersRulesWithAllowObfuscation(LIBRARY_CLASSES)
        .setMinApi(parameters)
        .addOptionsModification(
            options -> {
              options.inlinerOptions().enableInlining = false;
              options.getVerticalClassMergerOptions().disable();
              options.enableClassInlining = false;
            })
        .compile();
  }

  @Test
  public void runJvmProgramTest()
      throws ExecutionException, CompilationFailedException, IOException {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClasses(LIBRARY_CLASSES)
        .addProgramClasses(PROGRAM_CLASSES)
        .run(parameters.getRuntime(), ProgramClass.class)
        .assertSuccessWithOutput(EXPECTED_PROGRAM);
  }

  @Test
  public void runJvmProgramSubclassTest()
      throws ExecutionException, CompilationFailedException, IOException {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClasses(LIBRARY_CLASSES)
        .addProgramClasses(PROGRAM_CLASSES)
        .run(parameters.getRuntime(), ProgramSubclass.class)
        .assertSuccessWithOutput(EXPECTED_PROGRAM_SUBCLASS);
  }

  @Test
  public void testProgramClass()
      throws ExecutionException, CompilationFailedException, IOException {
    runTest(ProgramClass.class, EXPECTED_PROGRAM);
  }

  @Test
  public void testProgramSubClass()
      throws ExecutionException, IOException, CompilationFailedException {
    runTest(ProgramSubclass.class, EXPECTED_PROGRAM_SUBCLASS);
  }

  private void runTest(Class<?> main, String expected)
      throws ExecutionException, IOException, CompilationFailedException {
    R8TestCompileResult libraryCompileResult = compilationResults.apply(parameters);
    testForR8(parameters.getBackend())
        .noTreeShaking()
        .addDontObfuscate()
        .addProgramClasses(PROGRAM_CLASSES)
        .addApplyMapping(libraryCompileResult.getProguardMap())
        .addClasspathClasses(LIBRARY_CLASSES)
        .addLibraryFiles(parameters.getDefaultRuntimeLibrary())
        .setMinApi(parameters)
        .compile()
        .addRunClasspathFiles(libraryCompileResult.writeToZip())
        .run(parameters.getRuntime(), main)
        .assertSuccessWithOutput(expected);
  }
}
