// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.MarkerMatcher.assertMarkersMatch;
import static com.android.tools.r8.MarkerMatcher.markerTool;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.AssertionsConfiguration.AssertionTransformation;
import com.android.tools.r8.AssertionsConfiguration.AssertionTransformationScope;
import com.android.tools.r8.dex.Marker;
import com.android.tools.r8.dex.Marker.Tool;
import com.android.tools.r8.origin.EmbeddedOrigin;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.ThreadUtils;
import com.google.common.collect.ImmutableList;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class L8CommandTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public L8CommandTest(TestParameters parameters) {}

  @Test(expected = CompilationFailedException.class)
  public void emptyBuilder() throws Throwable {
    verifyEmptyCommand(L8Command.builder().build());
  }

  @Test
  public void emptyCommand() throws Throwable {
    verifyEmptyCommand(
        L8Command.builder()
            .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
            .addDesugaredLibraryConfiguration(
                StringResource.fromFile(ToolHelper.DESUGAR_LIB_JSON_FOR_TESTING))
            .build());
  }

  private void verifyEmptyCommand(L8Command command) {
    BaseCompilerCommand compilationCommand =
        command.getD8Command() == null ? command.getR8Command() : command.getD8Command();
    assertNotNull(compilationCommand);
    assertTrue(command.getProgramConsumer() instanceof ClassFileConsumer);
    assertTrue(compilationCommand.getProgramConsumer() instanceof DexIndexedConsumer);
  }

  @Test
  public void testDexMarker() throws Throwable {
    Path output = temp.newFolder().toPath().resolve("desugar_jdk_libs.zip");
    L8.run(
        L8Command.builder()
            .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
            .addProgramFiles(ToolHelper.getDesugarJDKLibs())
            .setMinApiLevel(20)
            .addDesugaredLibraryConfiguration(
                StringResource.fromFile(ToolHelper.DESUGAR_LIB_JSON_FOR_TESTING))
            .setOutput(output, OutputMode.DexIndexed)
            .build());
    assertMarkersMatch(
        ExtractMarker.extractMarkerFromDexFile(output),
        ImmutableList.of(markerTool(Tool.L8), markerTool(Tool.D8)));
  }

  @Test
  public void testClassFileMarker() throws Throwable {
    Path output = temp.newFolder().toPath().resolve("desugar_jdk_libs.zip");
    L8.run(
        L8Command.builder()
            .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
            .addProgramFiles(ToolHelper.getDesugarJDKLibs())
            .setMinApiLevel(20)
            .addDesugaredLibraryConfiguration(
                StringResource.fromFile(ToolHelper.DESUGAR_LIB_JSON_FOR_TESTING))
            .setOutput(output, OutputMode.ClassFile)
            .build());
    assertMarkersMatch(ExtractMarker.extractMarkerFromDexFile(output), markerTool(Tool.L8));
  }

  @Test
  public void testDexMarkerCommandLine() throws Throwable {
    Path output = temp.newFolder().toPath().resolve("desugar_jdk_libs.zip");
    L8Command l8Command =
        parse(
            ToolHelper.getDesugarJDKLibs().toString(),
            "--lib",
            ToolHelper.getAndroidJar(AndroidApiLevel.P).toString(),
            "--min-api",
            "20",
            "--desugared-lib",
            ToolHelper.DESUGAR_LIB_JSON_FOR_TESTING.toString(),
            "--output",
            output.toString());
    L8.run(l8Command);
    Collection<Marker> markers = ExtractMarker.extractMarkerFromDexFile(output);
    assertMarkersMatch(
        ExtractMarker.extractMarkerFromDexFile(output),
        ImmutableList.of(markerTool(Tool.L8), markerTool(Tool.D8)));
  }

  @Test
  public void testClassFileMarkerCommandLine() throws Throwable {
    Path output = temp.newFolder().toPath().resolve("desugar_jdk_libs.zip");
    L8Command l8Command =
        parse(
            ToolHelper.getDesugarJDKLibs().toString(),
            "--lib",
            ToolHelper.getAndroidJar(AndroidApiLevel.P).toString(),
            "--min-api",
            "20",
            "--desugared-lib",
            ToolHelper.DESUGAR_LIB_JSON_FOR_TESTING.toString(),
            "--output",
            output.toString(),
            "--classfile");
    L8.run(l8Command);
    assertMarkersMatch(ExtractMarker.extractMarkerFromDexFile(output), markerTool(Tool.L8));
  }

  @Test
  public void testFlagPgConf() throws Exception {
    TestDiagnosticMessagesImpl diagnostics = new TestDiagnosticMessagesImpl();
    Path pgconf = temp.newFolder().toPath().resolve("pg.conf");
    FileUtils.writeTextFile(pgconf, "");
    parse(
        diagnostics,
        "--desugared-lib",
        ToolHelper.DESUGAR_LIB_JSON_FOR_TESTING.toString(),
        "--pg-conf",
        pgconf.toString());
  }

  @Test
  public void testFlagPgConfWithClassFile() throws Exception {
    TestDiagnosticMessagesImpl diagnostics = new TestDiagnosticMessagesImpl();
    try {
      Path pgconf = temp.newFolder().toPath().resolve("pg.conf");
      FileUtils.writeTextFile(pgconf, "");
      parse(
          diagnostics,
          "--desugared-lib",
          ToolHelper.DESUGAR_LIB_JSON_FOR_TESTING.toString(),
          "--pg-conf",
          pgconf.toString(),
          "--classfile");
      fail("Expected failure");
    } catch (CompilationFailedException e) {
      diagnostics.assertErrorsMatch(
          diagnosticMessage(containsString("not support shrinking when generating class files")));
    }
  }

  @Test
  public void testFlagPgConfMissingParameter() {
    TestDiagnosticMessagesImpl diagnostics = new TestDiagnosticMessagesImpl();
    try {
      parse(
          diagnostics,
          "--desugared-lib",
          ToolHelper.DESUGAR_LIB_JSON_FOR_TESTING.toString(),
          "--pg-conf");
      fail("Expected parse error");
    } catch (CompilationFailedException e) {
      diagnostics.assertErrorsMatch(diagnosticMessage(containsString("Missing parameter")));
    }
  }

  private L8Command.Builder prepareBuilder(DiagnosticsHandler handler) {
    return L8Command.builder(handler)
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
        .addProgramFiles(ToolHelper.getDesugarJDKLibs())
        .setMinApiLevel(20);
  }

  @Test(expected = CompilationFailedException.class)
  public void mainDexListNotSupported() throws Throwable {
    Path mainDexList = temp.newFile("main-dex-list.txt").toPath();
    DiagnosticsChecker.checkErrorsContains(
        "L8 does not support a main dex list",
        (handler) ->
            prepareBuilder(handler)
                .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
                .addMainDexListFiles(mainDexList)
                .build());
  }

  @Test(expected = CompilationFailedException.class)
  public void dexPerClassNotSupported() throws Throwable {
    DiagnosticsChecker.checkErrorsContains(
        "L8 does not support compiling to dex per class",
        (handler) ->
            prepareBuilder(handler)
                .setProgramConsumer(DexFilePerClassFileConsumer.emptyConsumer())
                .build());
  }

  @Test(expected = CompilationFailedException.class)
  public void desugaredLibraryConfigurationRequired() throws Throwable {
    DiagnosticsChecker.checkErrorsContains(
        "L8 requires a desugared library configuration",
        (handler) ->
            prepareBuilder(handler).setProgramConsumer(ClassFileConsumer.emptyConsumer()).build());
  }

  private void addProguardConfigurationString(
      DiagnosticsHandler diagnostics, ProgramConsumer programConsumer) throws Throwable {
    String keepRule = "-keep class java.time.*";
    List<String> keepRules = new ArrayList<>();
    keepRules.add(keepRule);
    L8Command.Builder builder =
        prepareBuilder(diagnostics)
            .setProgramConsumer(programConsumer)
            .addDesugaredLibraryConfiguration(
                StringResource.fromFile(ToolHelper.DESUGAR_LIB_JSON_FOR_TESTING))
            .addProguardConfiguration(keepRules, Origin.unknown());
    assertTrue(builder.isShrinking());
    assertNotNull(builder.build().getR8Command());
  }

  @Test
  public void addProguardConfigurationStringWithDex() throws Throwable {
    addProguardConfigurationString(
        new TestDiagnosticMessagesImpl(), DexIndexedConsumer.emptyConsumer());
  }

  @Test
  public void addProguardConfigurationStringWithClassFile() throws Throwable {
    TestDiagnosticMessagesImpl diagnostics = new TestDiagnosticMessagesImpl();
    try {
      addProguardConfigurationString(diagnostics, ClassFileConsumer.emptyConsumer());
      fail("Expected failure");
    } catch (CompilationFailedException e) {
      diagnostics.assertErrorsMatch(
          diagnosticMessage(containsString("not support shrinking when generating class files")));
    }
  }

  private void addProguardConfigurationFile(
      DiagnosticsHandler diagnostics, ProgramConsumer programConsumer) throws Throwable {
    String keepRule = "-keep class java.time.*";
    Path keepRuleFile = temp.newFile("keepRuleFile.txt").toPath();
    Files.write(keepRuleFile, Collections.singletonList(keepRule), StandardCharsets.UTF_8);

    L8Command.Builder builder1 =
        prepareBuilder(diagnostics)
            .setProgramConsumer(programConsumer)
            .addDesugaredLibraryConfiguration(
                StringResource.fromFile(ToolHelper.DESUGAR_LIB_JSON_FOR_TESTING))
            .addProguardConfigurationFiles(keepRuleFile);
    assertTrue(builder1.isShrinking());
    assertNotNull(builder1.build().getR8Command());

    List<Path> keepRuleFiles = new ArrayList<>();
    keepRuleFiles.add(keepRuleFile);
    L8Command.Builder builder2 =
        prepareBuilder(new TestDiagnosticMessagesImpl())
            .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
            .addDesugaredLibraryConfiguration(
                StringResource.fromFile(ToolHelper.DESUGAR_LIB_JSON_FOR_TESTING))
            .addProguardConfigurationFiles(keepRuleFiles);
    assertTrue(builder2.isShrinking());
    assertNotNull(builder2.build().getR8Command());
  }

  @Test
  public void addProguardConfigurationFileDex() throws Throwable {
    addProguardConfigurationFile(
        new TestDiagnosticMessagesImpl(), DexIndexedConsumer.emptyConsumer());
  }

  @Test
  public void addProguardConfigurationFileClassFile() throws Throwable {
    TestDiagnosticMessagesImpl diagnostics = new TestDiagnosticMessagesImpl();
    try {
      addProguardConfigurationFile(diagnostics, ClassFileConsumer.emptyConsumer());
      fail("Expected failure");
    } catch (CompilationFailedException e) {
      diagnostics.assertErrorsMatch(
          diagnosticMessage(containsString("not support shrinking when generating class files")));
    }
  }

  @Test
  public void desugaredLibrary() throws CompilationFailedException {
    L8Command l8Command =
        parse("--desugared-lib", ToolHelper.DESUGAR_LIB_JSON_FOR_TESTING.toString());
    assertFalse(
        l8Command.getInternalOptions().desugaredLibraryConfiguration.getRewritePrefix().isEmpty());
  }

  private void checkSingleForceAllAssertion(
      List<AssertionsConfiguration> entries, AssertionTransformation transformation) {
    assertEquals(1, entries.size());
    assertEquals(transformation, entries.get(0).getTransformation());
    assertEquals(AssertionTransformationScope.ALL, entries.get(0).getScope());
  }

  private void checkSingleForceClassAndPackageAssertion(
      List<AssertionsConfiguration> entries, AssertionTransformation transformation) {
    assertEquals(2, entries.size());
    assertEquals(transformation, entries.get(0).getTransformation());
    assertEquals(AssertionTransformationScope.CLASS, entries.get(0).getScope());
    assertEquals("ClassName", entries.get(0).getValue());
    assertEquals(transformation, entries.get(1).getTransformation());
    assertEquals(AssertionTransformationScope.PACKAGE, entries.get(1).getScope());
    assertEquals("PackageName", entries.get(1).getValue());
  }

  @Test
  public void forceAssertionOption() throws Exception {
    checkSingleForceAllAssertion(
        parse(
                "--force-enable-assertions",
                "--desugared-lib",
                ToolHelper.DESUGAR_LIB_JSON_FOR_TESTING.toString())
            .getAssertionsConfiguration(),
        AssertionTransformation.ENABLE);
    checkSingleForceAllAssertion(
        parse(
                "--force-disable-assertions",
                "--desugared-lib",
                ToolHelper.DESUGAR_LIB_JSON_FOR_TESTING.toString())
            .getAssertionsConfiguration(),
        AssertionTransformation.DISABLE);
    checkSingleForceAllAssertion(
        parse(
                "--force-passthrough-assertions",
                "--desugared-lib",
                ToolHelper.DESUGAR_LIB_JSON_FOR_TESTING.toString())
            .getAssertionsConfiguration(),
        AssertionTransformation.PASSTHROUGH);
    checkSingleForceClassAndPackageAssertion(
        parse(
                "--force-enable-assertions:ClassName",
                "--force-enable-assertions:PackageName...",
                "--desugared-lib",
                ToolHelper.DESUGAR_LIB_JSON_FOR_TESTING.toString())
            .getAssertionsConfiguration(),
        AssertionTransformation.ENABLE);
    checkSingleForceClassAndPackageAssertion(
        parse(
                "--force-disable-assertions:ClassName",
                "--force-disable-assertions:PackageName...",
                "--desugared-lib",
                ToolHelper.DESUGAR_LIB_JSON_FOR_TESTING.toString())
            .getAssertionsConfiguration(),
        AssertionTransformation.DISABLE);
    checkSingleForceClassAndPackageAssertion(
        parse(
                "--force-passthrough-assertions:ClassName",
                "--force-passthrough-assertions:PackageName...",
                "--desugared-lib",
                ToolHelper.DESUGAR_LIB_JSON_FOR_TESTING.toString())
            .getAssertionsConfiguration(),
        AssertionTransformation.PASSTHROUGH);
  }

  @Test
  public void numThreadsOption() throws Exception {
    assertEquals(
        ThreadUtils.NOT_SPECIFIED,
        parse("--desugared-lib", ToolHelper.DESUGAR_LIB_JSON_FOR_TESTING.toString())
            .getThreadCount());
    assertEquals(
        1,
        parse(
                "--thread-count",
                "1",
                "--desugared-lib",
                ToolHelper.DESUGAR_LIB_JSON_FOR_TESTING.toString())
            .getThreadCount());
    assertEquals(
        2,
        parse(
                "--thread-count",
                "2",
                "--desugared-lib",
                ToolHelper.DESUGAR_LIB_JSON_FOR_TESTING.toString())
            .getThreadCount());
    assertEquals(
        10,
        parse(
                "--thread-count",
                "10",
                "--desugared-lib",
                ToolHelper.DESUGAR_LIB_JSON_FOR_TESTING.toString())
            .getThreadCount());
  }

  private void numThreadsOptionInvalid(String value) throws Exception {
    final String expectedErrorContains = "Invalid argument to --thread-count";
    try {
      DiagnosticsChecker.checkErrorsContains(
          expectedErrorContains,
          handler ->
              parse(
                  handler,
                  "--thread-count",
                  value,
                  "--desugared-lib",
                  ToolHelper.DESUGAR_LIB_JSON_FOR_TESTING.toString()));
      fail("Expected failure");
    } catch (CompilationFailedException e) {
      // Expected.
    }
  }

  @Test
  public void numThreadsOptionInvalid() throws Exception {
    numThreadsOptionInvalid("0");
    numThreadsOptionInvalid("-1");
    numThreadsOptionInvalid("two");
  }

  private L8Command parse(String... args) throws CompilationFailedException {
    return L8Command.parse(args, EmbeddedOrigin.INSTANCE).build();
  }

  private L8Command parse(DiagnosticsHandler handler, String... args)
      throws CompilationFailedException {
    return L8Command.parse(args, EmbeddedOrigin.INSTANCE, handler).build();
  }
}
