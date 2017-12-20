// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.dexmerger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationException;
import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ArtCommandBuilder;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.maindexlist.MainDexListTests;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.OutputMode;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutionException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DexMergerTests {

  private static final String CLASS_DIR = ToolHelper.EXAMPLES_BUILD_DIR + "classes/dexmergesample";
  private static final String CLASS1_CLASS = CLASS_DIR + "/Class1.class";
  private static final String CLASS2_CLASS = CLASS_DIR + "/Class2.class";
  private static final int MAX_METHOD_COUNT = Constants.U16BIT_MAX;

  @Rule public TemporaryFolder temp = ToolHelper.getTemporaryFolderForTest();

  @Test
  public void mergeTwoFiles() throws CompilationFailedException, IOException {
    // Compile Class1 and Class2
    Path outDir1 = temp.newFolder().toPath();
    Path outDir2 = temp.newFolder().toPath();
    D8.run(
        D8Command.builder()
            .setOutput(outDir1, OutputMode.DexIndexed)
            .addProgramFiles(Paths.get(CLASS1_CLASS))
            .build());
    D8.run(
        D8Command.builder()
            .setOutput(outDir2, OutputMode.DexIndexed)
            .addProgramFiles(Paths.get(CLASS2_CLASS))
            .build());

    // Merge the two dex files.
    Path dex1 = outDir1.resolve("classes.dex");
    Path dex2 = outDir2.resolve("classes.dex");
    Path outDex = temp.getRoot().toPath().resolve("out.dex");
    DexMerger.main(new String[] {outDex.toString(), dex1.toString(), dex2.toString()});

    // Test by running methods of Class1 and Class2
    for (String className : new String[] {"Class1", "Class2"}) {
      ArtCommandBuilder builder = new ArtCommandBuilder();
      builder.appendClasspath(outDex.toString());
      builder.setMainClass("dexmergesample." + className);
      String out = ToolHelper.runArt(builder);
      assertEquals(out, className + "\n");
    }
  }

  @Test(expected = DexMerger.ResultTooBigForSingleDexException.class)
  public void failIfTooBig()
      throws IOException, ExecutionException, CompilationException, CompilationFailedException {
    // Generates an application with two classes, each with the number of methods just enough not to
    // fit into a single dex file.
    AndroidApp generated =
        MainDexListTests.generateApplication(
            ImmutableList.of("A", "B"), AndroidApiLevel.N.getLevel(), MAX_METHOD_COUNT / 2 + 2);
    Path appDir = temp.newFolder().toPath();
    generated.write(appDir, OutputMode.Indexed);
    Path dex1 = appDir.resolve("classes.dex");
    Path dex2 = appDir.resolve("classes2.dex");
    assertTrue(dex1.toFile().exists());
    assertTrue(dex2.toFile().exists());

    // Merge the two dex files.
    Path outDex = temp.getRoot().toPath().resolve("out.dex");
    DexMerger.run(new String[] {outDex.toString(), dex1.toString(), dex2.toString()});
  }
}
