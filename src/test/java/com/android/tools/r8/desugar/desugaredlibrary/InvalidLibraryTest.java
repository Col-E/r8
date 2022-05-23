// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
//  for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.D8_L8DEBUG;
import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.D8_L8SHRINK;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;
import static org.hamcrest.core.StringContains.containsString;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.CustomLibrarySpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.errors.InvalidLibrarySuperclassDiagnostic;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InvalidLibraryTest extends DesugaredLibraryTestBase {

  private static final String EXPECTED_RESULT =
      StringUtils.lines("1970-01-02T10:17:36.789Z", "1970-01-12T10:20:54.321123456Z");
  private static final String INVALID_RESULT =
      StringUtils.lines("1970-01-02T10:17:36.789Z", "1970-01-12T10:20:54.321Z");

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(),
        getJdk8Jdk11(),
        ImmutableList.of(D8_L8DEBUG, D8_L8SHRINK));
  }

  public InvalidLibraryTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  private Path getSuperclassAsClasspath() throws CompilationFailedException, IOException {
    return testForD8(getStaticTemp())
        .addProgramClasses(SuperLibraryClass.class)
        .setMinApi(AndroidApiLevel.B)
        .compile()
        .writeToZip();
  }

  @Test
  public void testProgramSupertype() throws Exception {
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addProgramClasses(
            Executor.class, SuperLibraryClass.class, LocalClass.class, LocalClassOverride.class)
        .setCustomLibrarySpecification(
            new CustomLibrarySpecification(CustomLibraryClass.class, AndroidApiLevel.B))
        .addKeepMainRule(Executor.class)
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testClasspathSupertype() throws Exception {
    Assume.assumeTrue(
        "Date is present in the library above O",
        parameters.getApiLevel().isLessThan(AndroidApiLevel.O));
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addProgramClasses(Executor.class, LocalClass.class, LocalClassOverride.class)
        .addClasspathClasses(SuperLibraryClass.class)
        .setCustomLibrarySpecification(
            new CustomLibrarySpecification(CustomLibraryClass.class, AndroidApiLevel.B))
        .addKeepMainRule(Executor.class)
        .compile()
        .addRunClasspathFiles(getSuperclassAsClasspath())
        .run(parameters.getRuntime(), Executor.class)
        // The code requires desugaring to be run correctly, but with the classpath superclass,
        // desugaring is incorrectly performed. The code therefore falls-backs to the default
        // implementation in Date, which happens to be correct in one case, but incorrect
        // in the other case (Warning was raised).
        .assertSuccessWithOutput(INVALID_RESULT);
  }

  @Test
  public void testNullSupertype() throws Exception {
    Assume.assumeTrue(
        "Date is present in the library above O",
        parameters.getApiLevel().isLessThan(AndroidApiLevel.O));
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addProgramClasses(Executor.class, LocalClass.class, LocalClassOverride.class)
        .setCustomLibrarySpecification(
            new CustomLibrarySpecification(CustomLibraryClass.class, AndroidApiLevel.B))
        .addKeepMainRule(Executor.class)
        .compile()
        .inspectDiagnosticMessages(this::assertWarningInvalidLibrary)
        .addRunClasspathFiles(getSuperclassAsClasspath())
        .run(parameters.getRuntime(), Executor.class)
        // The code requires desugaring to be run correctly, but with the missing supertype,
        // desugaring could not be performed and the code cannot simply run (Warning was raised).
        .assertFailureWithErrorThatMatches(containsString("NoSuchMethodError"));
  }

  private void assertWarningInvalidLibrary(TestDiagnosticMessages testDiagnosticMessages) {
    assert testDiagnosticMessages.getWarnings().stream()
        .anyMatch(diagnostic -> diagnostic instanceof InvalidLibrarySuperclassDiagnostic);
  }

  static class Executor {
    public static void main(String[] args) {
      System.out.println(new LocalClass(123456789).toInstant());
      System.out.println(getOverrideAsLocalClass().toInstant());
    }

    public static LocalClass getOverrideAsLocalClass() {
      return new LocalClassOverride(987654321);
    }
  }

  static class SuperLibraryClass extends Date {
    public SuperLibraryClass(int nanos) {
      super(nanos);
    }
  }

  static class CustomLibraryClass extends SuperLibraryClass {
    public CustomLibraryClass(int nanos) {
      super(nanos);
    }
  }

  static class LocalClass extends CustomLibraryClass {
    public LocalClass(int nanos) {
      super(nanos);
    }
  }

  static class LocalClassOverride extends LocalClass {
    public LocalClassOverride(int nanos) {
      super(nanos);
    }

    @Override
    public Instant toInstant() {
      return super.toInstant().plusNanos(123456);
    }
  }
}
