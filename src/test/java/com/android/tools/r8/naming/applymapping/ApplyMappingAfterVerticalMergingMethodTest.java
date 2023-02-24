// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.applymapping;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverPropagateValue;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ApplyMappingAfterVerticalMergingMethodTest extends TestBase {

  private static final String OUTPUT = "LibraryBase::foo";
  private static final String EXPECTED_SUCCESS = StringUtils.lines(OUTPUT);

  // Base class will be vertical class merged into subclass
  public static class LibraryBase {

    @NeverPropagateValue
    @NeverInline
    public String foo() {
      return OUTPUT;
    }
  }

  // Subclass targeted via vertical class merging. The main method ensures a reference to foo.
  public static class LibrarySubclass extends LibraryBase {

    public static void main(String[] args) {
      System.out.println(new LibrarySubclass().foo());
    }
  }

  // Program class that uses LibrarySubclass and accesses foo via its main.
  public static class ProgramClass extends LibrarySubclass {

    public static void main(String[] args) {
      LibrarySubclass.main(args);
    }
  }

  // Test runner code follows.

  // Result of the shared compilation.
  private static class CompilationResult {
    final R8TestCompileResult library;
    final R8TestCompileResult program;
    final Path libraryPath;

    public CompilationResult(
        R8TestCompileResult library, R8TestCompileResult program, Path libraryPath) {
      this.library = library;
      this.program = program;
      this.libraryPath = libraryPath;
    }
  }

  private static final Class<?>[] LIBRARY_CLASSES = {LibraryBase.class, LibrarySubclass.class};

  private static final Class<?>[] PROGRAM_CLASSES = {
      ProgramClass.class
  };

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().build();
  }

  private static Function<Backend, CompilationResult> compilationResults =
      memoizeFunction(ApplyMappingAfterVerticalMergingMethodTest::compile);

  public static CompilationResult compile(Backend backend)
      throws CompilationFailedException, IOException {
    R8TestCompileResult library = compileLibrary(backend);
    R8TestCompileResult program = compileProgram(backend, library.getProguardMap());
    return new CompilationResult(library, program, library.writeToZip());
  }

  private static R8TestCompileResult compileLibrary(Backend backend)
      throws CompilationFailedException, IOException {
    return testForR8(getStaticTemp(), backend)
        .enableInliningAnnotations()
        .enableMemberValuePropagationAnnotations()
        .addProgramClasses(LIBRARY_CLASSES)
        .addKeepMainRule(LibrarySubclass.class)
        .addKeepClassAndDefaultConstructor(LibrarySubclass.class)
        .addVerticallyMergedClassesInspector(
            inspector -> inspector.assertMergedIntoSubtype(LibraryBase.class))
        .setMinApi(AndroidApiLevel.B)
        .compile()
        .inspect(
            inspector -> {
              assertThat(inspector.clazz(LibraryBase.class), not(isPresent()));
              assertThat(inspector.clazz(LibrarySubclass.class), isPresent());
              List<FoundMethodSubject> methods =
                  inspector.clazz(LibrarySubclass.class).allMethods();
              assertEquals(4, methods.size());
              assertEquals(
                  1, methods.stream().filter(FoundMethodSubject::isInstanceInitializer).count());
              assertEquals(
                  1, methods.stream().filter(m -> m.getFinalName().contains("main")).count());
              assertEquals(
                  2, methods.stream().filter(m -> m.getOriginalName().contains("foo")).count());
            });
  }

  private static R8TestCompileResult compileProgram(Backend backend, String proguardMap)
      throws CompilationFailedException {
    return testForR8(getStaticTemp(), backend)
        .noTreeShaking()
        .addDontObfuscate()
        .addProgramClasses(PROGRAM_CLASSES)
        .addApplyMapping(proguardMap)
        .addLibraryClasses(LIBRARY_CLASSES)
        .addLibraryFiles(runtimeJar(backend))
        .setMinApi(AndroidApiLevel.B)
        .compile();
  }

  private TestParameters parameters;

  public ApplyMappingAfterVerticalMergingMethodTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void runOnJvm() throws Throwable {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClasses(LIBRARY_CLASSES)
        .addProgramClasses(PROGRAM_CLASSES)
        .run(parameters.getRuntime(), ProgramClass.class)
        .assertSuccessWithOutput(EXPECTED_SUCCESS);
  }

  @Test
  public void b121042934() throws Exception {
    CompilationResult compilationResult = compilationResults.apply(parameters.getBackend());
    compilationResult
        .program
        .addRunClasspathFiles(compilationResult.libraryPath)
        .run(parameters.getRuntime(), ProgramClass.class)
        .assertSuccessWithOutput(EXPECTED_SUCCESS);
  }
}
