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
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.zip.ZipFile;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EmptyDesugaredLibrary extends DesugaredLibraryTestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public EmptyDesugaredLibrary(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  private L8Command.Builder prepareL8Builder(AndroidApiLevel minApiLevel) {
    return L8Command.builder()
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
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

  private int firstEmptyLevel() {
    return isJDK11DesugaredLibrary()
        // Some desugarings are required on all API levels including UNKNOWN.
        ? AndroidApiLevel.NOT_SET.getLevel()
        : AndroidApiLevel.O.getLevel();
  }

  @Test
  public void testEmptyDesugaredLibrary() throws Exception {
    for (AndroidApiLevel apiLevel : AndroidApiLevel.values()) {
      if (apiLevel.getLevel() < AndroidApiLevel.K.getLevel()) {
        // No need to test all API levels.
        continue;
      }
      CountingProgramConsumer programConsumer = new CountingProgramConsumer();
      ToolHelper.runL8(prepareL8Builder(apiLevel).setProgramConsumer(programConsumer).build());
      assertEquals(apiLevel.getLevel() >= firstEmptyLevel() ? 0 : 1, programConsumer.count);
    }
  }

  @Test
  public void testEmptyDesugaredLibraryDexZip() throws Exception {
    for (AndroidApiLevel apiLevel : AndroidApiLevel.values()) {
      if (apiLevel.getLevel() < AndroidApiLevel.K.getLevel()) {
        // No need to test all API levels.
        continue;
      }
      Path desugaredLibraryZip = temp.newFolder().toPath().resolve("desugar_jdk_libs_dex.zip");
      ToolHelper.runL8(
          prepareL8Builder(apiLevel).setOutput(desugaredLibraryZip, OutputMode.DexIndexed).build());
      assertTrue(Files.exists(desugaredLibraryZip));
      assertEquals(
          apiLevel.getLevel() >= firstEmptyLevel() ? 0 : 1,
          new ZipFile(desugaredLibraryZip.toFile(), StandardCharsets.UTF_8).size());
    }
  }

  @Test
  public void testEmptyDesugaredLibraryDexDirectory() throws Exception {
    for (AndroidApiLevel apiLevel : AndroidApiLevel.values()) {
      if (apiLevel.getLevel() < AndroidApiLevel.K.getLevel()) {
        // No need to test all API levels.
        continue;
      }
      Path desugaredLibraryDirectory = temp.newFolder().toPath();
      ToolHelper.runL8(
          prepareL8Builder(apiLevel)
              .setOutput(desugaredLibraryDirectory, OutputMode.DexIndexed)
              .build());
      assertEquals(
          apiLevel.getLevel() >= firstEmptyLevel() ? 0 : 1,
          Files.walk(desugaredLibraryDirectory)
              .filter(path -> path.toString().endsWith(".dex"))
              .count());
    }
  }
}
