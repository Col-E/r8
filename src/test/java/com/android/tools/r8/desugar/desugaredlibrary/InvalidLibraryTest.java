// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
//  for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static org.hamcrest.core.StringContains.containsString;

import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.errors.InvalidLibrarySuperclassDiagnostic;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class InvalidLibraryTest extends DesugaredLibraryTestBase {

  private static Path customLib;
  private static Path superclassAsClasspath;
  private static final String EXPECTED_RESULT =
      StringUtils.lines("1970-01-02T10:17:36.789Z", "1970-01-12T10:20:54.321123456Z");
  private static final String INVALID_RESULT =
      StringUtils.lines("1970-01-02T10:17:36.789Z", "1970-01-12T10:20:54.321Z");

  private final TestParameters parameters;
  private final boolean shrinkDesugaredLibrary;

  @Parameterized.Parameters(name = "{1}, shrinkDesugaredLibrary: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withDexRuntimes().withAllApiLevels().build());
  }

  public InvalidLibraryTest(boolean shrinkDesugaredLibrary, TestParameters parameters) {
    this.shrinkDesugaredLibrary = shrinkDesugaredLibrary;
    this.parameters = parameters;
  }

  @BeforeClass
  public static void compileCustomLib() throws Exception {
    customLib =
        testForD8(getStaticTemp())
            .addProgramClasses(CustomLibraryClass.class)
            .setMinApi(AndroidApiLevel.B)
            .compile()
            .writeToZip();
    superclassAsClasspath =
        testForD8(getStaticTemp())
            .addProgramClasses(SuperLibraryClass.class)
            .setMinApi(AndroidApiLevel.B)
            .compile()
            .writeToZip();
  }

  @Test
  public void testProgramSupertype() throws Exception {
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    testForD8()
        .setMinApi(parameters.getApiLevel())
        .addProgramClasses(
            Executor.class, SuperLibraryClass.class, LocalClass.class, LocalClassOverride.class)
        .addLibraryClasses(CustomLibraryClass.class)
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .compile()
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibrary,
            parameters.getApiLevel(),
            keepRuleConsumer.get(),
            shrinkDesugaredLibrary)
        .addRunClasspathFiles(customLib)
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testClasspathSupertype() throws Exception {
    Assume.assumeTrue(requiresAnyCoreLibDesugaring(parameters));
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    testForD8()
        .setMinApi(parameters.getApiLevel())
        .addProgramClasses(Executor.class, LocalClass.class, LocalClassOverride.class)
        .addClasspathClasses(SuperLibraryClass.class)
        .addLibraryClasses(CustomLibraryClass.class)
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .compile()
        .inspectDiagnosticMessages(this::assertWarningInvalidLibrary)
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibrary,
            parameters.getApiLevel(),
            keepRuleConsumer.get(),
            shrinkDesugaredLibrary)
        .addRunClasspathFiles(customLib, superclassAsClasspath)
        .run(parameters.getRuntime(), Executor.class)
        // The code requires desugaring to be run correctly, but with the classpath superclass,
        // desugaring is incorrectly performed. The code therefore falls-backs to the default
        // implementation in Date, which happens to be correct in one case, but incorrect
        // in the other case (Warning was raised).
        .assertSuccessWithOutput(INVALID_RESULT);
  }

  @Test
  public void testNullSupertype() throws Exception {
    Assume.assumeTrue(requiresAnyCoreLibDesugaring(parameters));
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    testForD8()
        .setMinApi(parameters.getApiLevel())
        .addProgramClasses(Executor.class, LocalClass.class, LocalClassOverride.class)
        .addLibraryClasses(CustomLibraryClass.class)
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .compile()
        .inspectDiagnosticMessages(this::assertWarningInvalidLibrary)
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibrary,
            parameters.getApiLevel(),
            keepRuleConsumer.get(),
            shrinkDesugaredLibrary)
        .addRunClasspathFiles(customLib, superclassAsClasspath)
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
