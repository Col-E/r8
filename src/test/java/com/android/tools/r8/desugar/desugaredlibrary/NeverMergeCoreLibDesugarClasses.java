// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.D8_L8DEBUG;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.smali.SmaliBuilder;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.List;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NeverMergeCoreLibDesugarClasses extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(),
        getJdk8Jdk11(),
        ImmutableList.of(D8_L8DEBUG));
  }

  public NeverMergeCoreLibDesugarClasses(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  @Test
  public void testMimimalDexFile() throws Exception {
    SmaliBuilder builder = new SmaliBuilder();
    builder.addClass("j$.util.function.Function");
    builder.addStaticMethod("void", "method", ImmutableList.of(), 0, "return-void");

    try {
      testForD8()
          .addInnerClasses(NeverMergeCoreLibDesugarClasses.class)
          .addProgramDexFileData(builder.compile())
          .setMinApi(parameters)
          .compileWithExpectedDiagnostics(
              diagnostics -> {
                diagnostics.assertErrorsCount(1);
                String message = diagnostics.getErrors().get(0).getDiagnosticMessage();
                assertThat(
                    message,
                    containsString(
                        "Merging DEX file containing classes with prefix 'j$.' "
                            + "with other classes, except classes with prefix 'java.', "
                            + "is not allowed:"));
              });
    } catch (CompilationFailedException e) {
      // Expected compilation failed.
      return;
    }
    fail("Expected test to fail with CompilationFailedException");
  }

  @Test
  public void testDesugaredCoreLibrary() throws Exception {
    Assume.assumeTrue(parameters.getApiLevel().getLevel() < AndroidApiLevel.O.getLevel());
    try {
      Path input =
          testForL8(parameters.getApiLevel())
              .apply(libraryDesugaringSpecification::configureL8TestBuilder)
              .compile()
              .writeToZip();
      testForD8()
          .addInnerClasses(NeverMergeCoreLibDesugarClasses.class)
          .addLibraryFiles(libraryDesugaringSpecification.getLibraryFiles())
          .setMinApi(parameters)
          .addProgramFiles(input)
          .compileWithExpectedDiagnostics(
              diagnostics -> {
                diagnostics.assertErrorsCount(1);
                String message = diagnostics.getErrors().get(0).getDiagnosticMessage();
                assertThat(
                    message,
                    containsString(
                        "Merging DEX file containing classes with prefix 'j$.' "
                            + "with other classes, except classes with prefix 'java.', "
                            + "is not allowed:"));
              });
    } catch (CompilationFailedException e) {
      // Expected compilation failed.
      return;
    }
    fail("Expected test to fail with CompilationFailedException");
  }

  @Test
  public void testTestCodeRuns() throws Exception {
    // j$.util.Function is not present in recent APIs.
    Assume.assumeTrue(parameters.getApiLevel().getLevel() < AndroidApiLevel.N.getLevel());
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addInnerClasses(getClass())
        .run(
            parameters.getRuntime(),
            TestClass.class,
            libraryDesugaringSpecification.functionPrefix(parameters))
        .assertSuccessWithOutputLines("Hello, world!");
  }

  static class TestClass {

    public static void main(String[] args) throws Exception {
      Class.forName(args[0] + ".util.function.Function");
      System.out.println("Hello, world!");
    }
  }
}
