// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.D8_L8DEBUG;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;
import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.LibraryDesugaringTestConfiguration;
import com.android.tools.r8.StringResource;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.origin.Origin;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DesugaredLibraryMismatchTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final CompilationSpecification compilationSpecification;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters()
            .withDexRuntime(Version.first())
            .withDefaultDexRuntime()
            .withDexRuntime(Version.last())
            .withOnlyDexRuntimeApiLevel()
            .build(),
        getJdk8Jdk11(),
        ImmutableList.of(D8_L8DEBUG));
  }

  public DesugaredLibraryMismatchTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.compilationSpecification = compilationSpecification;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
  }

  @Test
  public void testInputDexed() throws Exception {
    // DEX code without library desugaring.
    Path libraryDex =
        testForD8(Backend.DEX)
            .addProgramClasses(Library.class)
            .setMinApi(parameters.getApiLevel())
            .compile()
            .writeToZip();

    // Combine DEX input without library desugaring with dexing with library desugaring.
    try {
      testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
          .addProgramFiles(libraryDex)
          .addProgramClasses(TestRunner.class)
          .compileWithExpectedDiagnostics(
              diagnostics -> {
                if (libraryDesugaringSpecification.hasAnyDesugaring(parameters)) {
                  diagnostics.assertNoInfos();
                  diagnostics.assertAllWarningsMatch(
                      diagnosticMessage(
                          containsString(
                              "The compilation is slowed down due to a mix of class file and dex"
                                  + " file inputs in the context of desugared library.")));
                } else {
                  diagnostics.assertNoMessages();
                }
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
            .setMinApi(parameters.getApiLevel())
            .compile()
            .writeToZip();

    // Combine CF desugared input without library desugaring with dexing with library desugaring.
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addProgramFiles(desugaredLibrary)
        .addProgramClasses(TestRunner.class)
        .compile();
  }

  @Test
  public void testInputCfDesugaredAndDexed() throws Exception {
    // CF to CF desugared code without library desugaring.
    Path desugaredLibrary =
        testForD8(Backend.CF)
            .addProgramClasses(Library.class)
            .setMinApi(parameters.getApiLevel())
            .compile()
            .writeToZip();

    // CF to CF desugared code without library desugaring compiled to DEX.
    Path desugaredLibraryDex =
        testForD8(Backend.DEX)
            .addProgramFiles(desugaredLibrary)
            .setMinApi(parameters.getApiLevel())
            .disableDesugaring()
            .compile()
            .writeToZip();

    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addProgramFiles(desugaredLibraryDex)
        .addProgramClasses(TestRunner.class)
        .compile();
  }

  @Test
  public void testCfInputLibraryDesugared() throws Exception {
    // CF to CF desugared code with library desugaring.
    Path desugaredLibrary =
        testForD8(Backend.CF)
            .addProgramClasses(Library.class)
            .setMinApi(parameters.getApiLevel())
            .enableCoreLibraryDesugaring(
                LibraryDesugaringTestConfiguration.forSpecification(
                    libraryDesugaringSpecification.getSpecification()))
            .compile()
            .writeToZip();

    // Combine CF input with library desugaring with dexing without library desugaring.
    testForD8()
        .addProgramFiles(desugaredLibrary)
        .addProgramClasses(TestRunner.class)
        .setMinApi(parameters.getApiLevel())
        .compile();
  }

  @Test
  public void testMergeLibraryDesugaredWithNotLibraryDesugared() throws Exception {
    // DEX code with library desugaring.
    Path libraryDex =
        testForDesugaredLibrary(
                parameters, libraryDesugaringSpecification, compilationSpecification)
            .addProgramClasses(Library.class)
            .compile()
            .writeToZip();

    // DEX code without library desugaring.
    Path programDex =
        testForD8(Backend.DEX)
            .addProgramClasses(TestRunner.class)
            .setMinApi(parameters.getApiLevel())
            .compile()
            .writeToZip();

    testForD8()
        .addProgramFiles(libraryDex)
        .addProgramFiles(programDex)
        .setMinApi(parameters.getApiLevel())
        .compile();
  }

  @Test
  public void testMergeDifferentLibraryDesugarVersions() throws Exception {
    // DEX code with library desugaring using a desugared library configuration with a
    // different identifier.
    Path libraryDex =
        testForD8(Backend.DEX)
            .addProgramClasses(Library.class)
            .setMinApi(parameters.getApiLevel())
            .enableCoreLibraryDesugaring(
                LibraryDesugaringTestConfiguration.builder()
                    // Minimal configuration with a different identifier.
                    // The j$.time is rewritten because empty flags are equivalent to an empty
                    // specification, and no marker is set for empty specifications.
                    .addDesugaredLibraryConfiguration(
                        StringResource.fromString(
                            "{"
                                + "\"configuration_format_version\":3,"
                                + "\"group_id\":\"my_group\","
                                + "\"artifact_id\":\"my_artifact\","
                                + "\"version\":\"1.0.9\","
                                + "\"synthesized_library_classes_package_prefix\":\"my_prefix\","
                                + "\"required_compilation_api_level\":\"30\","
                                + "\"common_flags\":[{"
                                + "      \"api_level_below_or_equal\": 9999,"
                                + "      \"rewrite_prefix\": {\"j$.time.\": \"java.time.\"}"
                                + "    }],"
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
            .setMinApi(parameters.getApiLevel())
            .compile()
            .writeToZip();

    testForD8()
        .addProgramFiles(libraryDex)
        .addProgramFiles(programDex)
        .setMinApi(parameters.getApiLevel())
        .compile();
  }

  static class Library {}

  static class TestRunner {

    public static void main(String[] args) {
      System.out.println(Library.class);
    }
  }
}
