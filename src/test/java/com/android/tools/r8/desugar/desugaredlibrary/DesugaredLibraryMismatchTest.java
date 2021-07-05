// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.LibraryDesugaringTestConfiguration;
import com.android.tools.r8.StringResource;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.errors.DesugaredLibraryMismatchDiagnostic;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibraryConfiguration;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.Box;
import java.nio.file.Path;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DesugaredLibraryMismatchTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final AndroidApiLevel apiLevel;

  @Parameters(name = "API level: {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withNoneRuntime().build(),
        new AndroidApiLevel[] {
          AndroidApiLevel.LATEST, AndroidApiLevel.O, AndroidApiLevel.N_MR1, AndroidApiLevel.B
        });
  }

  public DesugaredLibraryMismatchTest(TestParameters parameters, AndroidApiLevel apiLevel) {
    this.parameters = parameters;
    this.apiLevel = apiLevel;
  }

  @Test
  public void testInputDexed() throws Exception {
    // DEX code without library desugaring.
    Path libraryDex =
        testForD8(Backend.DEX)
            .addProgramClasses(Library.class)
            .setMinApi(apiLevel)
            .compile()
            .writeToZip();

    // Combine DEX input without library desugaring with dexing with library desugaring.
    try {
      testForD8()
          .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
          .addProgramFiles(libraryDex)
          .addProgramClasses(TestRunner.class)
          .setMinApi(apiLevel)
          .enableCoreLibraryDesugaring(LibraryDesugaringTestConfiguration.forApiLevel(apiLevel))
          .compileWithExpectedDiagnostics(
              diagnostics -> {
                diagnostics.assertNoInfos();
                diagnostics.assertAllWarningsMatch(
                    diagnosticMessage(
                        containsString(
                            "The compilation is slowed down due to a mix of class file and dex"
                                + " file inputs in the context of desugared library.")));
                diagnostics.assertErrorsMatch(
                    diagnosticType(DesugaredLibraryMismatchDiagnostic.class));
              });

    } catch (CompilationFailedException e) {
    }
  }

  @Test
  public void testInputCfDesugared() throws Exception {
    // CF to CF desugared code without library desugaring.
    Path desugaredLibrary =
        testForD8(Backend.CF)
            .addProgramClasses(Library.class)
            .setMinApi(apiLevel)
            .compile()
            .writeToZip();

    // Combine CF desugared input without library desugaring with dexing with library desugaring.
    testForD8()
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
        .addProgramFiles(desugaredLibrary)
        .addProgramClasses(TestRunner.class)
        .setMinApi(apiLevel)
        .enableCoreLibraryDesugaring(LibraryDesugaringTestConfiguration.forApiLevel(apiLevel))
        .compile();
  }

  @Test
  public void testInputCfDesugaredAndDexed() throws Exception {
    // CF to CF desugared code without library desugaring.
    Path desugaredLibrary =
        testForD8(Backend.CF)
            .addProgramClasses(Library.class)
            .setMinApi(apiLevel)
            .compile()
            .writeToZip();

    // CF to CF desugared code without library desugaring compiled to DEX.
    Path desugaredLibraryDex =
        testForD8(Backend.DEX)
            .addProgramFiles(desugaredLibrary)
            .setMinApi(apiLevel)
            .disableDesugaring()
            .compile()
            .writeToZip();

    testForD8()
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
        .addProgramFiles(desugaredLibraryDex)
        .addProgramClasses(TestRunner.class)
        .setMinApi(apiLevel)
        .enableCoreLibraryDesugaring(LibraryDesugaringTestConfiguration.forApiLevel(apiLevel))
        .compile();
  }

  @Test
  public void testCfInputLibraryDesugared() throws Exception {
    // CF to CF desugared code with library desugaring.
    Path desugaredLibrary =
        testForD8(Backend.CF)
            .addProgramClasses(Library.class)
            .setMinApi(apiLevel)
            .enableCoreLibraryDesugaring(LibraryDesugaringTestConfiguration.forApiLevel(apiLevel))
            .compile()
            .writeToZip();

    // Combine CF input with library desugaring with dexing without library desugaring.
    try {
      testForD8()
          .addProgramFiles(desugaredLibrary)
          .addProgramClasses(TestRunner.class)
          .setMinApi(apiLevel)
          .compileWithExpectedDiagnostics(
              diagnostics -> {
                diagnostics.assertOnlyErrors();
                diagnostics.assertErrorsMatch(
                    diagnosticType(DesugaredLibraryMismatchDiagnostic.class));
              });
    } catch (CompilationFailedException e) {
    }
  }

  @Test
  public void testMergeLibraryDesugaredWithNotLibraryDesugared() throws Exception {
    // DEX code with library desugaring.
    Path libraryDex =
        testForD8(Backend.DEX)
            .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
            .addProgramClasses(Library.class)
            .setMinApi(apiLevel)
            .enableCoreLibraryDesugaring(LibraryDesugaringTestConfiguration.forApiLevel(apiLevel))
            .compile()
            .writeToZip();

    // DEX code without library desugaring.
    Path programDex =
        testForD8(Backend.DEX)
            .addProgramClasses(TestRunner.class)
            .setMinApi(apiLevel)
            .compile()
            .writeToZip();

    try {
      testForD8()
          .addProgramFiles(libraryDex)
          .addProgramFiles(programDex)
          .setMinApi(apiLevel)
          .compileWithExpectedDiagnostics(
              diagnostics -> {
                diagnostics.assertOnlyErrors();
                diagnostics.assertErrorsMatch(
                    diagnosticType(DesugaredLibraryMismatchDiagnostic.class));
              });
    } catch (CompilationFailedException e) {
    }
  }

  @Test
  public void testMergeDifferentLibraryDesugarVersions() throws Exception {
    // DEX code with library desugaring using a desugared library configuration with a
    // different identifier.
    Box<DesugaredLibraryConfiguration> box = new Box<>();
    Path libraryDex =
        testForD8(Backend.DEX)
            .addProgramClasses(Library.class)
            .setMinApi(apiLevel)
            .enableCoreLibraryDesugaring(
                LibraryDesugaringTestConfiguration.builder()
                    .setMinApi(apiLevel)
                    // Minimal configuration with a different identifier.
                    .addDesugaredLibraryConfiguration(
                        StringResource.fromString(
                            "{"
                                + "\"configuration_format_version\":3,"
                                + "\"group_id\":\"my_group\","
                                + "\"artifact_id\":\"my_artifact\","
                                + "\"version\":\"1.0.9\","
                                + "\"synthesized_library_classes_package_prefix\":\"my_prefix\","
                                + "\"required_compilation_api_level\":\"30\","
                                + "\"common_flags\":[],"
                                + "\"library_flags\":[],"
                                + "\"program_flags\":[]"
                                + "}",
                            Origin.unknown()))
                    .build())
            .compile()
            .writeToZip();

    // DEX code without library desugaring.
    Path programDex =
        testForD8(Backend.DEX)
            .addProgramClasses(TestRunner.class)
            .setMinApi(apiLevel)
            .compile()
            .writeToZip();

    try {
      testForD8()
          .addProgramFiles(libraryDex)
          .addProgramFiles(programDex)
          .setMinApi(apiLevel)
          .compileWithExpectedDiagnostics(
              diagnostics -> {
                diagnostics.assertOnlyErrors();
                diagnostics.assertErrorsMatch(
                    allOf(
                        diagnosticType(DesugaredLibraryMismatchDiagnostic.class),
                        diagnosticMessage(containsString("my_group:my_artifact:1.0.9"))));
              });
    } catch (CompilationFailedException e) {
    }
  }

  static class Library {}

  static class TestRunner {

    public static void main(String[] args) {
      System.out.println(Library.class);
    }
  }
}
