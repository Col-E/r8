// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.android.tools.r8.MarkerMatcher.assertMarkersMatch;
import static com.android.tools.r8.MarkerMatcher.markerCompilationMode;
import static com.android.tools.r8.MarkerMatcher.markerDesugaredLibraryIdentifier;
import static com.android.tools.r8.MarkerMatcher.markerHasChecksums;
import static com.android.tools.r8.MarkerMatcher.markerHasDesugaredLibraryIdentifier;
import static com.android.tools.r8.MarkerMatcher.markerHasMinApi;
import static com.android.tools.r8.MarkerMatcher.markerIsDesugared;
import static com.android.tools.r8.MarkerMatcher.markerMinApi;
import static com.android.tools.r8.MarkerMatcher.markerR8Mode;
import static com.android.tools.r8.MarkerMatcher.markerTool;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.dex.Marker;
import com.android.tools.r8.dex.Marker.Tool;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.gson.JsonParser;
import java.nio.file.Path;
import java.util.Collection;
import org.hamcrest.Matcher;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MarkersTest extends TestBase {

  @Parameterized.Parameters(name = "{0}, compilationMode {1}, shrinkDesugaredLibrary {2}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withNoneRuntime().build(),
        CompilationMode.values(),
        BooleanUtils.values());
  }

  private final TestParameters parameters;
  private final CompilationMode compilationMode;
  private final boolean shrinkDesugaredLibrary;

  public MarkersTest(
      TestParameters parameters, CompilationMode compilationMode, boolean shrinkDesugaredLibrary) {
    this.parameters = parameters;
    this.compilationMode = compilationMode;
    this.shrinkDesugaredLibrary = shrinkDesugaredLibrary;
  }

  @Test
  public void testL8Marker() throws Throwable {
    AndroidApiLevel apiLevel = AndroidApiLevel.L;
    Path output = temp.newFolder().toPath().resolve("desugar_jdk_libs.zip");
    L8Command.Builder builder =
        L8Command.builder()
            .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
            .addProgramFiles(ToolHelper.getDesugarJDKLibs())
            .setMinApiLevel(apiLevel.getLevel())
            .setMode(compilationMode)
            .addDesugaredLibraryConfiguration(
                StringResource.fromFile(ToolHelper.DESUGAR_LIB_JSON_FOR_TESTING))
            .setOutput(output, OutputMode.DexIndexed);
    if (shrinkDesugaredLibrary) {
      builder.addProguardConfiguration(ImmutableList.of("-keep class * { *; }"), Origin.unknown());
    }
    L8.run(builder.build());
    Collection<Marker> markers = ExtractMarker.extractMarkerFromDexFile(output);
    String version =
        new JsonParser()
            .parse(FileUtils.readTextFile(ToolHelper.DESUGAR_LIB_JSON_FOR_TESTING, Charsets.UTF_8))
            .getAsJsonObject()
            .get("version")
            .getAsString();

    Matcher<Marker> l8Matcher =
        allOf(
            markerTool(Tool.L8),
            markerCompilationMode(compilationMode),
            markerDesugaredLibraryIdentifier("com.tools.android:desugar_jdk_libs:" + version),
            markerHasChecksums(false));
    Matcher<Marker> d8Matcher =
        allOf(
            markerTool(Tool.R8),
            markerCompilationMode(compilationMode),
            markerMinApi(apiLevel),
            markerR8Mode("compatibility"));
    Matcher<Marker> r8Matcher =
        allOf(markerTool(Tool.D8), markerCompilationMode(compilationMode), markerMinApi(apiLevel));
    if (shrinkDesugaredLibrary) {
      assertMarkersMatch(markers, ImmutableList.of(l8Matcher, d8Matcher));
    } else {
      assertMarkersMatch(markers, ImmutableList.of(l8Matcher, r8Matcher));
    }
  }

  @Test
  public void testD8MarkerInDex() throws Throwable {
    AndroidApiLevel apiLevel = AndroidApiLevel.L;
    Path output = temp.newFolder().toPath().resolve("output.zip");
    D8Command.Builder builder =
        D8Command.builder()
            .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
            .addProgramFiles(ToolHelper.getClassFileForTestClass(TestClass.class))
            .setMode(compilationMode)
            .setMinApiLevel(apiLevel.getLevel())
            .setOutput(output, OutputMode.DexIndexed);
    D8.run(builder.build());
    Collection<Marker> markers = ExtractMarker.extractMarkerFromDexFile(output);
    Matcher<Marker> matcher =
        allOf(
            markerTool(Tool.D8),
            markerCompilationMode(compilationMode),
            markerIsDesugared(),
            markerMinApi(apiLevel),
            not(markerHasDesugaredLibraryIdentifier()));
    assertMarkersMatch(markers, matcher);
  }

  @Test
  public void testD8MarkerInCf() throws Throwable {
    // Shrinking of desugared library is not affecting this test.
    assumeTrue(shrinkDesugaredLibrary);

    AndroidApiLevel apiLevel = AndroidApiLevel.L;
    Path output = temp.newFolder().toPath().resolve("output.zip");
    D8Command.Builder builder =
        D8Command.builder()
            .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
            .addProgramFiles(ToolHelper.getClassFileForTestClass(TestClass.class))
            .setMode(compilationMode)
            .setMinApiLevel(apiLevel.getLevel())
            .setOutput(output, OutputMode.ClassFile);
    D8.run(builder.build());
    Collection<Marker> markers = ExtractMarker.extractMarkerFromDexFile(output);
    Matcher<Marker> matcher =
        allOf(
            markerTool(Tool.D8),
            markerCompilationMode(compilationMode),
            markerIsDesugared(),
            markerMinApi(apiLevel),
            not(markerHasDesugaredLibraryIdentifier()));
    assertMarkersMatch(markers, matcher);
  }

  @Test
  public void testR8MarkerInDex() throws Throwable {
    // Shrinking of desugared library is not affecting this test.
    assumeTrue(shrinkDesugaredLibrary);

    AndroidApiLevel apiLevel = AndroidApiLevel.L;
    Path output = temp.newFolder().toPath().resolve("output.zip");
    R8Command.Builder builder =
        R8Command.builder()
            .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
            .addProgramFiles(ToolHelper.getClassFileForTestClass(TestClass.class))
            .addProguardConfiguration(ImmutableList.of("-keep class * { *; }"), Origin.unknown())
            .setMode(compilationMode)
            .setMinApiLevel(apiLevel.getLevel())
            .setOutput(output, OutputMode.DexIndexed);
    R8.run(builder.build());
    Collection<Marker> markers = ExtractMarker.extractMarkerFromDexFile(output);
    Matcher<Marker> matcher =
        allOf(
            markerTool(Tool.R8),
            markerCompilationMode(compilationMode),
            markerIsDesugared(),
            markerMinApi(apiLevel),
            not(markerHasDesugaredLibraryIdentifier()));
    assertMarkersMatch(markers, matcher);
  }

  @Test
  public void testR8MarkerInCf() throws Throwable {
    // Shrinking of desugared library is not affecting this test.
    assumeTrue(shrinkDesugaredLibrary);

    Path output = temp.newFolder().toPath().resolve("output.zip");
    R8Command.Builder builder =
        R8Command.builder()
            .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
            .addProgramFiles(ToolHelper.getClassFileForTestClass(TestClass.class))
            .addProguardConfiguration(ImmutableList.of("-keep class * { *; }"), Origin.unknown())
            .setMode(compilationMode)
            .setOutput(output, OutputMode.ClassFile);
    R8.run(builder.build());
    Collection<Marker> markers = ExtractMarker.extractMarkerFromDexFile(output);
    Matcher<Marker> matcher =
        allOf(
            markerTool(Tool.R8),
            markerCompilationMode(compilationMode),
            not(markerIsDesugared()),
            not(markerHasMinApi()),
            not(markerHasDesugaredLibraryIdentifier()));
    assertMarkersMatch(markers, matcher);
  }

  @Test
  public void testR8MarkerInCfAfterD8CfDesugar() throws Throwable {
    // Shrinking of desugared library is not affecting this test.
    assumeTrue(shrinkDesugaredLibrary);

    AndroidApiLevel apiLevel = AndroidApiLevel.L;
    Path d8DesugaredOutput = temp.newFolder().toPath().resolve("output.zip");
    D8.run(
        D8Command.builder()
            .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
            .addProgramFiles(ToolHelper.getClassFileForTestClass(TestClass.class))
            .setMode(compilationMode)
            .setMinApiLevel(apiLevel.getLevel())
            .setOutput(d8DesugaredOutput, OutputMode.ClassFile)
            .build());
    assertMarkersMatch(
        ExtractMarker.extractMarkerFromDexFile(d8DesugaredOutput),
        allOf(
            markerTool(Tool.D8),
            markerCompilationMode(compilationMode),
            markerIsDesugared(),
            markerMinApi(apiLevel),
            not(markerHasDesugaredLibraryIdentifier())));

    // Running R8 on desugared input will clear that information and leave no markers with
    // that information.
    Path output = temp.newFolder().toPath().resolve("output.zip");
    R8.run(
        R8Command.builder()
            .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
            .addProgramFiles(ToolHelper.getClassFileForTestClass(TestClass.class))
            .addProguardConfiguration(ImmutableList.of("-keep class * { *; }"), Origin.unknown())
            .setMode(compilationMode)
            .setOutput(output, OutputMode.ClassFile)
            .build());
    assertMarkersMatch(
        ExtractMarker.extractMarkerFromDexFile(output),
        allOf(
            markerTool(Tool.R8),
            markerCompilationMode(compilationMode),
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
