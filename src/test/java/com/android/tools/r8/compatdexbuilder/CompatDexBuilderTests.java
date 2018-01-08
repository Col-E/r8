// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.compatdexbuilder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ArtCommandBuilder;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class CompatDexBuilderTests {

  @Rule public TemporaryFolder temp = ToolHelper.getTemporaryFolderForTest();

  @Test
  public void compileManyClasses() throws IOException, InterruptedException, ExecutionException {
    final String SUBDIR = "naming001";
    final String INPUT_JAR = ToolHelper.TESTS_BUILD_DIR + "examples/" + SUBDIR + ".jar";
    final List<String> CLASS_NAMES =
        ImmutableList.of(
            "A",
            "B",
            "C",
            "D",
            "E",
            "F",
            "G",
            "H",
            "I",
            "J",
            "K",
            "L",
            "Reflect2$A",
            "Reflect2$B",
            "Reflect2",
            "Reflect");

    // Run CompatDexBuilder on naming001.jar
    Path outputZip = temp.getRoot().toPath().resolve("out.zip");
    CompatDexBuilder.main(
        new String[] {"--input_jar", INPUT_JAR, "--output_zip", outputZip.toString()});
    assertTrue(outputZip.toFile().exists());

    // Verify if all the classes have their corresponding ".class.dex" files in the zip.
    Set<String> expectedNames = new HashSet<>();
    for (String className : CLASS_NAMES) {
      expectedNames.add(SUBDIR + "/" + className + ".class.dex");
    }
    try (ZipFile zipFile = new ZipFile(outputZip.toFile())) {
      for (Enumeration<? extends ZipEntry> e = zipFile.entries(); e.hasMoreElements(); ) {
        ZipEntry ze = e.nextElement();
        expectedNames.remove(ze.getName());
      }
    }
    assertTrue(expectedNames.isEmpty());
  }

  @Test
  public void compileTwoClassesAndRun()
      throws IOException, InterruptedException, ExecutionException, CompilationFailedException {
    // Run CompatDexBuilder on dexMergeSample.jar
    final String INPUT_JAR = ToolHelper.EXAMPLES_BUILD_DIR + "dexmergesample.jar";
    Path outputZip = temp.getRoot().toPath().resolve("out.zip");
    CompatDexBuilder.main(
        new String[] {"--input_jar", INPUT_JAR, "--output_zip", outputZip.toString()});

    // Merge zip content into a single dex file.
    Path d8OutDir = temp.newFolder().toPath();
    D8.run(
        D8Command.builder()
            .setOutput(d8OutDir, OutputMode.DexIndexed)
            .addProgramFiles(outputZip)
            .build());

    // Validate by running methods of Class1 and Class2
    for (String className : new String[] {"Class1", "Class2"}) {
      ArtCommandBuilder artCommandBuilder = new ArtCommandBuilder();
      artCommandBuilder.appendClasspath(d8OutDir.resolve("classes.dex").toString());
      artCommandBuilder.setMainClass("dexmergesample." + className);
      String out = ToolHelper.runArt(artCommandBuilder);
      assertEquals(out, className + "\n");
    }
  }
}
