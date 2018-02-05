// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.dexsplitter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ArtCommandBuilder;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DexSplitterTests {

  private static final String CLASS_DIR = ToolHelper.EXAMPLES_BUILD_DIR + "classes/dexsplitsample";
  private static final String CLASS1_CLASS = CLASS_DIR + "/Class1.class";
  private static final String CLASS2_CLASS = CLASS_DIR + "/Class2.class";
  private static final String CLASS3_CLASS = CLASS_DIR + "/Class3.class";
  private static final String CLASS3_INNER_CLASS = CLASS_DIR + "/Class3$InnerClass.class";

  @Rule public TemporaryFolder temp = ToolHelper.getTemporaryFolderForTest();

  /**
   * To test the file splitting we have 3 classes that we distribute like this: Class1 -> base
   * Class2 -> feature1 Class3 -> feature1
   *
   * <p>Class1 and Class2 works independently of each other, but Class3 extends Class1, and
   * therefore can't run without the base being loaded.
   */
  @Test
  public void splitFiles() throws CompilationFailedException, IOException {
    // Initial normal compile to create dex files.
    Path inputDex = temp.newFolder().toPath().resolve("input.zip");
    D8.run(
        D8Command.builder()
            .setOutput(inputDex, OutputMode.DexIndexed)
            .addProgramFiles(Paths.get(CLASS1_CLASS))
            .addProgramFiles(Paths.get(CLASS2_CLASS))
            .addProgramFiles(Paths.get(CLASS3_CLASS))
            .addProgramFiles(Paths.get(CLASS3_INNER_CLASS))
            .build());

    Path outputDex = temp.getRoot().toPath().resolve("output");
    Path splitSpec = temp.getRoot().toPath().resolve("split_spec");
    try (PrintWriter out = new PrintWriter(splitSpec.toFile(), "UTF-8")) {
      out.write(
          "dexsplitsample.Class1:base\n"
              + "dexsplitsample.Class2:feature1\n"
              + "dexsplitsample.Class3:feature1");
    }

    DexSplitter.main(
        new String[] {
          "--input", inputDex.toString(),
          "--output", outputDex.toString(),
          "--feature-splits", splitSpec.toString()
        });

    // Both classes should still work if we give all dex files to the system.
    Path base = outputDex.getParent().resolve("output.base.zip");
    Path feature = outputDex.getParent().resolve("output.feature1.zip");
    for (String className : new String[] {"Class1", "Class2", "Class3"}) {
      ArtCommandBuilder builder = new ArtCommandBuilder();
      builder.appendClasspath(base.toString());
      builder.appendClasspath(feature.toString());
      builder.setMainClass("dexsplitsample." + className);
      String out = ToolHelper.runArt(builder);
      assertEquals(out, className + "\n");
    }
    // Individual classes should also work from the individual files.
    String className = "Class1";
    ArtCommandBuilder builder = new ArtCommandBuilder();
    builder.appendClasspath(base.toString());
    builder.setMainClass("dexsplitsample." + className);
    String out = ToolHelper.runArt(builder);
    assertEquals(out, className + "\n");

    className = "Class2";
    builder = new ArtCommandBuilder();
    builder.appendClasspath(feature.toString());
    builder.setMainClass("dexsplitsample." + className);
    out = ToolHelper.runArt(builder);
    assertEquals(out, className + "\n");

    className = "Class3";
    builder = new ArtCommandBuilder();
    builder.appendClasspath(feature.toString());
    builder.setMainClass("dexsplitsample." + className);
    try {
      ToolHelper.runArt(builder);
      assertFalse(true);
    } catch (AssertionError assertionError) {
      // We expect this to throw since base is not in the path and Class3 depends on Class1
    }
  }
}
