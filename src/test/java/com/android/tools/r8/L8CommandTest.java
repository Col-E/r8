// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.sdklib.AndroidVersion;
import com.android.tools.r8.dex.Marker;
import com.android.tools.r8.dex.Marker.Tool;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import java.nio.file.Path;
import java.util.Collection;
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
        L8Command.builder().setProgramConsumer(DexIndexedConsumer.emptyConsumer()).build());
  }

  private void verifyEmptyCommand(L8Command command) throws Throwable {
    assertEquals(CompilationMode.DEBUG, command.getMode());
    assertEquals(AndroidVersion.DEFAULT.getApiLevel(), command.getMinApiLevel());
    assertTrue(command.getProgramConsumer() instanceof DexIndexedConsumer);
    AndroidApp app = ToolHelper.getApp(command);
    assertEquals(0, app.getDexProgramResourcesForTesting().size());
    assertEquals(0, app.getClassProgramResourcesForTesting().size());
  }

  @Test
  public void testMarker() throws Throwable {
    Path output = temp.newFolder().toPath().resolve("desugar_jdk_libs.zip");
    L8.run(
        L8Command.builder()
            .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
            .addProgramFiles(ToolHelper.getDesugarJDKLibs())
            .setMinApiLevel(20)
            .setOutput(output, OutputMode.DexIndexed)
            .build());
    Collection<Marker> markers = ExtractMarker.extractMarkerFromDexFile(output);
    assertEquals(1, markers.size());
    Marker marker = markers.iterator().next();
    assertEquals(20, marker.getMinApi().intValue());
    assertEquals(Tool.L8, marker.getTool());
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
        "L8 does not support compiling to Java class files",
        (handler) ->
            prepareBuilder(handler).setProgramConsumer(ClassFileConsumer.emptyConsumer()).build());
  }

  @Test
  public void warnForSpecialLibraryConfiguration() throws Throwable {
    DiagnosticsChecker.checkWarningsContains(
        "Special library configuration is still work in progress",
        handler ->
            prepareBuilder(handler)
                .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
                .addSpecialLibraryConfiguration("")
                .build());
  }
}
