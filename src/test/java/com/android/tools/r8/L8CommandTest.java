// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.dex.Marker;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class L8CommandTest {

  @Rule
  public TemporaryFolder temp = ToolHelper.getTemporaryFolderForTest();

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
  public void testMarker() throws Throwable {
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
    Collection<Marker> markers = ExtractMarker.extractMarkerFromDexFile(output);
    // TODO(b/134732760): Shouldn't we remove the D8/R8 marker?
    assertEquals(2, markers.size());
    Marker marker = markers.iterator().next();
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
  public void classFileOutputNotSupported() throws Throwable {
    DiagnosticsChecker.checkErrorsContains(
        "L8 does not support compiling to class files",
        (handler) ->
            prepareBuilder(handler).setProgramConsumer(ClassFileConsumer.emptyConsumer()).build());
  }

  @Test(expected = CompilationFailedException.class)
  public void desugaredLibraryConfigurationRequired() throws Throwable {
    DiagnosticsChecker.checkErrorsContains(
        "L8 requires a desugared library configuration",
        (handler) ->
            prepareBuilder(handler).setProgramConsumer(ClassFileConsumer.emptyConsumer()).build());
  }

  @Test
  public void addProguardConfigurationString() throws Throwable {
    String keepRule = "-keep class java.time.*";
    List<String> keepRules = new ArrayList<>();
    keepRules.add(keepRule);
    L8Command.Builder builder =
        prepareBuilder(new TestDiagnosticMessagesImpl())
            .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
            .addDesugaredLibraryConfiguration(
                StringResource.fromFile(ToolHelper.DESUGAR_LIB_JSON_FOR_TESTING))
            .addProguardConfiguration(keepRules, Origin.unknown());
    assertTrue(builder.isShrinking());
    assertNotNull(builder.build().getR8Command());
  }

  @Test
  public void addProguardConfigurationFile() throws Throwable {
    String keepRule = "-keep class java.time.*";
    Path keepRuleFile = temp.newFile("keepRuleFile.txt").toPath();
    Files.write(keepRuleFile, Collections.singletonList(keepRule), StandardCharsets.UTF_8);

    L8Command.Builder builder1 =
        prepareBuilder(new TestDiagnosticMessagesImpl())
            .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
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
}
