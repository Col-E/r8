// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.android.tools.r8.MarkerMatcher.assertMarkersMatch;
import static com.android.tools.r8.MarkerMatcher.markerBackend;
import static com.android.tools.r8.MarkerMatcher.markerCompilationMode;
import static com.android.tools.r8.MarkerMatcher.markerDesugaredLibraryIdentifier;
import static com.android.tools.r8.MarkerMatcher.markerHasChecksums;
import static com.android.tools.r8.MarkerMatcher.markerHasDesugaredLibraryIdentifier;
import static com.android.tools.r8.MarkerMatcher.markerHasMinApi;
import static com.android.tools.r8.MarkerMatcher.markerIsDesugared;
import static com.android.tools.r8.MarkerMatcher.markerMinApi;
import static com.android.tools.r8.MarkerMatcher.markerR8Mode;
import static com.android.tools.r8.MarkerMatcher.markerTool;
import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.DEFAULT_SPECIFICATIONS;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.dex.Marker;
import com.android.tools.r8.dex.Marker.Tool;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.ExtractMarkerUtils;
import com.android.tools.r8.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Path;
import java.util.Collection;
import org.hamcrest.Matcher;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MarkersTest extends DesugaredLibraryTestBase {

  @Parameterized.Parameters(
      name = "{0}, spec: {1}, compilationMode {2}, {3}, noCfMarkerForDesugaredCode {4}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withNoneRuntime().build(),
        LibraryDesugaringSpecification.getJdk8Jdk11(),
        DEFAULT_SPECIFICATIONS,
        BooleanUtils.values());
  }

  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;
  private final boolean noCfMarkerForDesugaredCode;

  public MarkersTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification,
      boolean noCfMarkerForDesugaredCode) {
    parameters.assertNoneRuntime();
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
    this.noCfMarkerForDesugaredCode = noCfMarkerForDesugaredCode;
  }

  @Test
  public void testL8Marker() throws Throwable {
    // This option is not relevant for L8.
    assumeTrue(!noCfMarkerForDesugaredCode);

    AndroidApiLevel apiLevel = AndroidApiLevel.L;
    Path output = temp.newFolder().toPath().resolve("desugar_jdk_libs.zip");
    CompilationMode compilationMode =
        compilationSpecification.isL8Shrink() ? CompilationMode.RELEASE : CompilationMode.DEBUG;
    L8Command.Builder builder =
        L8Command.builder()
            .addLibraryFiles(libraryDesugaringSpecification.getLibraryFiles())
            .addProgramFiles(libraryDesugaringSpecification.getDesugarJdkLibs())
            .setMinApiLevel(apiLevel.getLevel())
            .setMode(compilationMode)
            .addDesugaredLibraryConfiguration(
                StringResource.fromFile(libraryDesugaringSpecification.getSpecification()))
            .setOutput(output, OutputMode.DexIndexed);
    if (compilationSpecification.isL8Shrink()) {
      builder.addProguardConfiguration(ImmutableList.of("-keep class * { *; }"), Origin.unknown());
    }
    L8.run(builder.build());
    Collection<Marker> markers = ExtractMarkerUtils.extractMarkersFromFile(output);
    JsonObject jsonObject =
        new JsonParser()
            .parse(
                FileUtils.readTextFile(
                    libraryDesugaringSpecification.getSpecification(), Charsets.UTF_8))
            .getAsJsonObject();
    String identifier =
        jsonObject.has("version")
            ? "com.tools.android:desugar_jdk_libs:" + jsonObject.get("version").getAsString()
            : jsonObject.get("identifier").getAsString();

    Matcher<Marker> l8Matcher =
        allOf(
            markerTool(Tool.L8),
            markerCompilationMode(compilationMode),
            markerDesugaredLibraryIdentifier(identifier),
            markerHasChecksums(false));
    Matcher<Marker> r8Matcher =
        allOf(
            markerTool(Tool.R8),
            markerCompilationMode(compilationMode),
            markerMinApi(apiLevel),
            markerR8Mode("full"));
    Matcher<Marker> d8Matcher =
        allOf(markerTool(Tool.D8), markerCompilationMode(compilationMode), markerMinApi(apiLevel));
    assertMarkersMatch(
        markers,
        ImmutableList.of(l8Matcher, compilationSpecification.isL8Shrink() ? r8Matcher : d8Matcher));
  }

  @Test
  public void testD8MarkerInDex() throws Throwable {
    AndroidApiLevel apiLevel = AndroidApiLevel.L;
    Path output = temp.newFolder().toPath().resolve("output.zip");
    D8Command.Builder builder =
        D8Command.builder()
            .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
            .addProgramFiles(ToolHelper.getClassFileForTestClass(TestClass.class))
            .setMode(compilationSpecification.getProgramCompilationMode())
            .setMinApiLevel(apiLevel.getLevel())
            .setOutput(output, OutputMode.DexIndexed);
    if (noCfMarkerForDesugaredCode) {
      ToolHelper.runD8(
          builder, options -> options.desugarSpecificOptions().noCfMarkerForDesugaredCode = true);
    } else {
      D8.run(builder.build());
    }
    Collection<Marker> markers = ExtractMarkerUtils.extractMarkersFromFile(output);
    Matcher<Marker> matcher =
        allOf(
            markerTool(Tool.D8),
            markerCompilationMode(compilationSpecification.getProgramCompilationMode()),
            markerBackend(Backend.DEX),
            markerIsDesugared(),
            markerMinApi(apiLevel),
            not(markerHasDesugaredLibraryIdentifier()));
    assertMarkersMatch(markers, matcher);
  }

  @Test
  public void testD8MarkerInCf() throws Throwable {
    // Shrinking of desugared library is not affecting this test.
    assumeTrue(compilationSpecification.isL8Shrink());

    AndroidApiLevel apiLevel = AndroidApiLevel.L;
    Path output = temp.newFolder().toPath().resolve("output.zip");
    D8Command.Builder builder =
        D8Command.builder()
            .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
            .addProgramFiles(ToolHelper.getClassFileForTestClass(TestClass.class))
            .setMode(compilationSpecification.getProgramCompilationMode())
            .setMinApiLevel(apiLevel.getLevel())
            .setOutput(output, OutputMode.ClassFile);
    if (noCfMarkerForDesugaredCode) {
      ToolHelper.runD8(
          builder, options -> options.desugarSpecificOptions().noCfMarkerForDesugaredCode = true);
      Collection<Marker> markers = ExtractMarkerUtils.extractMarkersFromFile(output);
      assertTrue(markers.isEmpty());
    } else {
      D8.run(builder.build());
      Collection<Marker> markers = ExtractMarkerUtils.extractMarkersFromFile(output);
      Matcher<Marker> matcher =
          allOf(
              markerTool(Tool.D8),
              markerCompilationMode(compilationSpecification.getProgramCompilationMode()),
              markerBackend(Backend.CF),
              markerIsDesugared(),
              markerMinApi(apiLevel),
              not(markerHasDesugaredLibraryIdentifier()));
      assertMarkersMatch(markers, matcher);
    }
  }

  @Test
  public void testR8MarkerInDex() throws Throwable {
    // Shrinking of desugared library is not affecting this test.
    assumeTrue(compilationSpecification.isL8Shrink());

    AndroidApiLevel apiLevel = AndroidApiLevel.L;
    Path output = temp.newFolder().toPath().resolve("output.zip");
    R8Command.Builder builder =
        R8Command.builder()
            .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
            .addProgramFiles(ToolHelper.getClassFileForTestClass(TestClass.class))
            .addProguardConfiguration(ImmutableList.of("-keep class * { *; }"), Origin.unknown())
            .setMode(compilationSpecification.getProgramCompilationMode())
            .setMinApiLevel(apiLevel.getLevel())
            .setOutput(output, OutputMode.DexIndexed);
    if (noCfMarkerForDesugaredCode) {
      ToolHelper.runR8(
          builder.build(),
          options -> options.desugarSpecificOptions().noCfMarkerForDesugaredCode = true);
    } else {
      R8.run(builder.build());
    }
    Collection<Marker> markers = ExtractMarkerUtils.extractMarkersFromFile(output);
    Matcher<Marker> matcher =
        allOf(
            markerTool(Tool.R8),
            markerCompilationMode(compilationSpecification.getProgramCompilationMode()),
            markerBackend(Backend.DEX),
            markerIsDesugared(),
            markerMinApi(apiLevel),
            not(markerHasDesugaredLibraryIdentifier()));
    assertMarkersMatch(markers, matcher);
  }

  @Test
  public void testR8MarkerInCf() throws Throwable {
    // Shrinking of desugared library is not affecting this test.
    assumeTrue(compilationSpecification.isL8Shrink());

    Path output = temp.newFolder().toPath().resolve("output.zip");
    R8Command.Builder builder =
        R8Command.builder()
            .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
            .addProgramFiles(ToolHelper.getClassFileForTestClass(TestClass.class))
            .addProguardConfiguration(ImmutableList.of("-keep class * { *; }"), Origin.unknown())
            .setMode(compilationSpecification.getProgramCompilationMode())
            .setOutput(output, OutputMode.ClassFile);
    if (noCfMarkerForDesugaredCode) {
      ToolHelper.runR8(
          builder.build(),
          options -> options.desugarSpecificOptions().noCfMarkerForDesugaredCode = true);
    } else {
      R8.run(builder.build());
    }
    Collection<Marker> markers = ExtractMarkerUtils.extractMarkersFromFile(output);
    Matcher<Marker> matcher =
        allOf(
            markerTool(Tool.R8),
            markerCompilationMode(compilationSpecification.getProgramCompilationMode()),
            markerBackend(Backend.CF),
            not(markerIsDesugared()),
            not(markerHasMinApi()),
            not(markerHasDesugaredLibraryIdentifier()));
    assertMarkersMatch(markers, matcher);
  }

  @Test
  public void testR8MarkerInCfAfterD8CfDesugar() throws Throwable {
    // Shrinking of desugared library is not affecting this test.
    assumeTrue(compilationSpecification.isL8Shrink());

    AndroidApiLevel apiLevel = AndroidApiLevel.L;
    Path d8DesugaredOutput = temp.newFolder().toPath().resolve("output.zip");
    D8Command.Builder builder =
        D8Command.builder()
            .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
            .addProgramFiles(ToolHelper.getClassFileForTestClass(TestClass.class))
            .setMode(compilationSpecification.getProgramCompilationMode())
            .setMinApiLevel(apiLevel.getLevel())
            .setOutput(d8DesugaredOutput, OutputMode.ClassFile);
    if (noCfMarkerForDesugaredCode) {
      ToolHelper.runD8(
          builder, options -> options.desugarSpecificOptions().noCfMarkerForDesugaredCode = true);
      Collection<Marker> markers = ExtractMarkerUtils.extractMarkersFromFile(d8DesugaredOutput);
      assertTrue(markers.isEmpty());
    } else {
      D8.run(builder.build());
      assertMarkersMatch(
          ExtractMarkerUtils.extractMarkersFromFile(d8DesugaredOutput),
          allOf(
              markerTool(Tool.D8),
              markerCompilationMode(compilationSpecification.getProgramCompilationMode()),
              markerIsDesugared(),
              markerMinApi(apiLevel),
              not(markerHasDesugaredLibraryIdentifier())));
    }
    // Running R8 on desugared input will clear that information and leave no markers with
    // that information.
    Path output = temp.newFolder().toPath().resolve("output.zip");
    R8.run(
        R8Command.builder()
            .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
            .addProgramFiles(ToolHelper.getClassFileForTestClass(TestClass.class))
            .addProguardConfiguration(ImmutableList.of("-keep class * { *; }"), Origin.unknown())
            .setMode(compilationSpecification.getProgramCompilationMode())
            .setOutput(output, OutputMode.ClassFile)
            .build());
    assertMarkersMatch(
        ExtractMarkerUtils.extractMarkersFromFile(output),
        allOf(
            markerTool(Tool.R8),
            markerCompilationMode(compilationSpecification.getProgramCompilationMode()),
            markerBackend(Backend.CF),
            not(markerIsDesugared()),
            not(markerHasMinApi()),
            not(markerHasDesugaredLibraryIdentifier())));
  }

  public static class TestClass {

    public static void main(String[] args) {
      System.out.println("Hello, world!");
    }
  }
}
