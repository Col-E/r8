// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.L8Command;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.StringResource;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipFile;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EmptyDesugaredLibrary extends DesugaredLibraryTestBase {

  private final AndroidApiLevel apiLevel;

  @Parameters(name = "api: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        range(AndroidApiLevel.K, AndroidApiLevel.ANDROID_PLATFORM),
        getTestParameters().withNoneRuntime().build());
  }

  private static List<AndroidApiLevel> range(
      AndroidApiLevel fromIncluding, AndroidApiLevel toExcluding) {
    ArrayList<AndroidApiLevel> result = new ArrayList<>();
    for (AndroidApiLevel apiLevel : AndroidApiLevel.values()) {
      if (apiLevel.isGreaterThanOrEqualTo(fromIncluding) && apiLevel.isLessThan(toExcluding)) {
        result.add(apiLevel);
      }
    }
    return result;
  }

  public EmptyDesugaredLibrary(AndroidApiLevel apiLevel, TestParameters parameters) {
    parameters.assertNoneRuntime();
    this.apiLevel = apiLevel;
  }

  private L8Command.Builder prepareL8Builder(AndroidApiLevel minApiLevel) {
    return L8Command.builder()
        .addLibraryFiles(getLibraryFile())
        .addProgramFiles(ToolHelper.getDesugarJDKLibs())
        .addDesugaredLibraryConfiguration(
            StringResource.fromFile(ToolHelper.getDesugarLibJsonForTesting()))
        .setMinApiLevel(minApiLevel.getLevel());
  }

  static class CountingProgramConsumer extends DexIndexedConsumer.ForwardingConsumer {

    public int count = 0;

    public CountingProgramConsumer() {
      super(null);
    }

    @Override
    public void accept(
        int fileIndex, ByteDataView data, Set<String> descriptors, DiagnosticsHandler handler) {
      count++;
    }
  }

  private boolean expectsEmptyDesugaredLibrary(AndroidApiLevel apiLevel) {
    if (isJDK11DesugaredLibrary()) {
      return false;
    }
    return apiLevel.isGreaterThanOrEqualTo(AndroidApiLevel.O);
  }

  @Test
  public void testEmptyDesugaredLibrary() throws Exception {
    CountingProgramConsumer programConsumer = new CountingProgramConsumer();
    ToolHelper.runL8(prepareL8Builder(apiLevel).setProgramConsumer(programConsumer).build());
    assertEquals(expectsEmptyDesugaredLibrary(apiLevel) ? 0 : 1, programConsumer.count);
  }

  @Test
  public void testEmptyDesugaredLibraryDexZip() throws Exception {
    Path desugaredLibraryZip = temp.newFolder().toPath().resolve("desugar_jdk_libs_dex.zip");
    ToolHelper.runL8(
        prepareL8Builder(apiLevel).setOutput(desugaredLibraryZip, OutputMode.DexIndexed).build());
    assertTrue(Files.exists(desugaredLibraryZip));
    assertEquals(
        expectsEmptyDesugaredLibrary(apiLevel) ? 0 : 1,
        new ZipFile(desugaredLibraryZip.toFile(), StandardCharsets.UTF_8).size());
  }

  @Test
  public void testEmptyDesugaredLibraryDexDirectory() throws Exception {
    Path desugaredLibraryDirectory = temp.newFolder().toPath();
    ToolHelper.runL8(
        prepareL8Builder(apiLevel)
            .setOutput(desugaredLibraryDirectory, OutputMode.DexIndexed)
            .build());
    assertEquals(
        expectsEmptyDesugaredLibrary(apiLevel) ? 0 : 1,
        Files.walk(desugaredLibraryDirectory)
            .filter(path -> path.toString().endsWith(".dex"))
            .count());
  }
}
