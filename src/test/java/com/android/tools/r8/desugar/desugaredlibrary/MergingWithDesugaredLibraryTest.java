// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.MarkerMatcher.assertMarkersMatch;
import static com.android.tools.r8.MarkerMatcher.markerBackend;
import static com.android.tools.r8.MarkerMatcher.markerCompilationMode;
import static com.android.tools.r8.MarkerMatcher.markerHasDesugaredLibraryIdentifier;
import static com.android.tools.r8.MarkerMatcher.markerIsDesugared;
import static com.android.tools.r8.MarkerMatcher.markerMinApi;
import static com.android.tools.r8.MarkerMatcher.markerTool;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.LibraryDesugaringTestConfiguration;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.dex.Marker;
import com.android.tools.r8.dex.Marker.Tool;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.ExtractMarkerUtils;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.hamcrest.Matcher;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MergingWithDesugaredLibraryTest extends DesugaredLibraryTestBase {

  private static final String JAVA_RESULT = "java.util.stream.ReferencePipeline$Head";
  private static final String J$_RESULT = "j$.util.stream.ReferencePipeline$Head";

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;

  @Parameters(name = "{0}, spec: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(), getJdk8Jdk11());
  }

  public MergingWithDesugaredLibraryTest(
      TestParameters parameters, LibraryDesugaringSpecification libraryDesugaringSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
  }

  @Test
  public void testMergeDesugaredAndNonDesugared() throws Exception {
    D8TestCompileResult compileResult =
        testForD8()
            .addLibraryFiles(libraryDesugaringSpecification.getLibraryFiles())
            .addProgramFiles(buildPart1DesugaredLibrary(), buildPart2NoDesugaredLibrary())
            .setMinApi(parameters)
            .applyIf(
                someLibraryDesugaringRequired(),
                b ->
                    b.enableCoreLibraryDesugaring(
                        LibraryDesugaringTestConfiguration.forSpecification(
                            libraryDesugaringSpecification.getSpecification())))
            .compile()
            .addRunClasspathFiles(
                getNonShrunkDesugaredLib(parameters, libraryDesugaringSpecification));
    compileResult
        .run(parameters.getRuntime(), Part1.class)
        .assertSuccessWithOutputLines(getExpected());
    boolean expectNoSuchMethodError =
        parameters.isDexRuntime() && parameters.getDexRuntimeVersion().isOlderThan(Version.V7_0_0);
    compileResult
        .run(parameters.getRuntime(), Part2.class)
        .assertFailureWithErrorThatThrowsIf(expectNoSuchMethodError, NoSuchMethodError.class)
        .assertSuccessWithOutputLinesIf(!expectNoSuchMethodError, JAVA_RESULT);
  }

  private String getExpected() {
    if (parameters.getApiLevel().getLevel() >= AndroidApiLevel.N.getLevel()) {
      return JAVA_RESULT;
    } else {
      return J$_RESULT;
    }
  }

  @Test
  public void testMergeDesugaredWithShrunkenLib() throws Exception {
    // Compile a library with R8 to CF.
    Path shrunkenLib =
        testForR8(Backend.CF)
            .addProgramClasses(Part2.class)
            .addKeepClassRules(Part2.class)
            .compile()
            .writeToZip();

    // R8 class file output marker has no library desugaring identifier.
    Matcher<Marker> libraryMatcher =
        allOf(
            markerTool(Tool.R8),
            markerCompilationMode(CompilationMode.RELEASE),
            not(markerIsDesugared()),
            not(markerHasDesugaredLibraryIdentifier()));
    assertMarkersMatch(ExtractMarkerUtils.extractMarkersFromFile(shrunkenLib), libraryMatcher);

    // Build an app with the R8 compiled library.
    Path app =
        testForD8()
            .addProgramFiles(buildPart1DesugaredLibrary(), shrunkenLib)
            .addLibraryFiles(libraryDesugaringSpecification.getLibraryFiles())
            .setMinApi(parameters)
            .applyIf(
                someLibraryDesugaringRequired(),
                b ->
                    b.enableCoreLibraryDesugaring(
                        LibraryDesugaringTestConfiguration.forSpecification(
                            libraryDesugaringSpecification.getSpecification())))
            .compile()
            .writeToZip();

    // The app has both the R8 marker from the library compilation and the D8 marker from dexing.
    Matcher<Marker> d8Matcher =
        allOf(
            markerTool(Tool.D8),
            markerIsDesugared(),
            markerHasDesugaredLibraryIdentifier(
                libraryDesugaringSpecification.hasAnyDesugaring(parameters)));
    assertMarkersMatch(
        ExtractMarkerUtils.extractMarkersFromFile(app),
        ImmutableList.of(libraryMatcher, d8Matcher));
  }

  @Test
  public void testMergeDesugaredWithDesugaredLib() throws Exception {
    // Compile a library with D8 to CF.
    Path desugaredLibCf =
        testForD8(Backend.CF)
            .addProgramClasses(Part2.class)
            .setMinApi(parameters)
            .compile()
            .writeToZip();

    // D8 class file output marker has desugaring but no library desugaring.
    Matcher<Marker> markerMatcher =
        allOf(
            markerTool(Tool.D8),
            markerCompilationMode(CompilationMode.DEBUG),
            markerBackend(Backend.CF),
            markerIsDesugared(),
            markerMinApi(parameters.getApiLevel()),
            not(markerHasDesugaredLibraryIdentifier()));
    assertMarkersMatch(ExtractMarkerUtils.extractMarkersFromFile(desugaredLibCf), markerMatcher);

    Path desugaredLibDex =
        testForD8()
            .addProgramFiles(desugaredLibCf)
            .disableDesugaring()
            .setMinApi(parameters)
            .compile()
            .writeToZip();

    // D8 dex file output marker has the same marker as the D8 class file output.
    Matcher<Marker> dexMarkerMatcher =
        allOf(
            markerTool(Tool.D8),
            markerCompilationMode(CompilationMode.DEBUG),
            markerBackend(Backend.DEX),
            markerIsDesugared(),
            markerMinApi(parameters.getApiLevel()),
            not(markerHasDesugaredLibraryIdentifier()));
    List<Matcher<Marker>> markerMatcherAfterDex = ImmutableList.of(markerMatcher, dexMarkerMatcher);
    assertMarkersMatch(
        ExtractMarkerUtils.extractMarkersFromFile(desugaredLibDex), markerMatcherAfterDex);

    // Build an app using library desugaring merging with library not using library desugaring.
    Path app;
    app =
        testForD8()
            .addProgramFiles(buildPart1DesugaredLibrary(), desugaredLibDex)
            .setMinApi(parameters)
            .compile()
            .writeToZip();

    // When there is no class-file resources we are adding the marker for the last compilation.
    List<Matcher<Marker>> expectedMarkers = new ArrayList<>();
    expectedMarkers.add(markerMatcher);
    expectedMarkers.add(dexMarkerMatcher);
    if (someLibraryDesugaringRequired()) {
      expectedMarkers.add(
          allOf(
              markerTool(Tool.D8),
              markerCompilationMode(CompilationMode.DEBUG),
              markerBackend(Backend.DEX),
              markerIsDesugared(),
              markerMinApi(parameters.getApiLevel()),
              markerHasDesugaredLibraryIdentifier()));
    }
    assertMarkersMatch(ExtractMarkerUtils.extractMarkersFromFile(app), expectedMarkers);
  }

  private boolean expectError() {
    return someLibraryDesugaringRequired();
  }

  private boolean someLibraryDesugaringRequired() {
    return libraryDesugaringSpecification.hasAnyDesugaring(parameters);
  }

  @Test
  public void testMergeDesugaredAndClassFile() throws Exception {
    D8TestCompileResult compileResult =
        testForD8()
            .addProgramFiles(buildPart1DesugaredLibrary())
            .addProgramClasses(Part2.class)
            .addLibraryFiles(libraryDesugaringSpecification.getLibraryFiles())
            .setMinApi(parameters)
            .applyIf(
                someLibraryDesugaringRequired(),
                b ->
                    b.enableCoreLibraryDesugaring(
                        LibraryDesugaringTestConfiguration.forSpecification(
                            libraryDesugaringSpecification.getSpecification())))
            .compile()
            .addRunClasspathFiles(
                getNonShrunkDesugaredLib(parameters, libraryDesugaringSpecification))
            .inspectDiagnosticMessages(this::assertWarningPresent);
    compileResult
        .run(parameters.getRuntime(), Part1.class)
        .assertSuccessWithOutputLines(getExpected());
    compileResult
        .run(parameters.getRuntime(), Part2.class)
        .assertSuccessWithOutputLines(getExpected());
  }

  private void assertWarningPresent(TestDiagnosticMessages testDiagnosticMessages) {
    if (!someLibraryDesugaringRequired()) {
      return;
    }
    assertTrue(
        testDiagnosticMessages.getWarnings().stream()
            .anyMatch(
                warn -> warn.getDiagnosticMessage().startsWith("The compilation is slowed down")));
  }

  private Path buildPart1DesugaredLibrary() throws Exception {
    return testForD8()
        .addLibraryFiles(libraryDesugaringSpecification.getLibraryFiles())
        .addProgramClasses(Part1.class)
        .setMinApi(parameters)
        .applyIf(
            someLibraryDesugaringRequired(),
            b ->
                b.enableCoreLibraryDesugaring(
                    LibraryDesugaringTestConfiguration.forSpecification(
                        libraryDesugaringSpecification.getSpecification())))
        .compile()
        .writeToZip();
  }

  private Path buildPart2NoDesugaredLibrary() throws Exception {
    return testForD8().addProgramClasses(Part2.class).setMinApi(parameters).compile().writeToZip();
  }

  @SuppressWarnings("RedundantOperationOnEmptyContainer")
  static class Part1 {

    public static void main(String[] args) {
      System.out.println(new ArrayList<>().stream().getClass().getName());
    }
  }

  @SuppressWarnings("RedundantOperationOnEmptyContainer")
  static class Part2 {

    public static void main(String[] args) {
      System.out.println(new ArrayList<>().stream().getClass().getName());
    }
  }
}
