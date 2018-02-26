// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.debuginfo;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ArtCommandBuilder;
import com.android.tools.r8.ToolHelper.ProcessResult;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PreamblePositionTestRunner {

  private static final String TEST_CLASS = "PreamblePositionTestSource";
  private static final String TEST_PACKAGE = "com.android.tools.r8.debuginfo";

  @ClassRule public static TemporaryFolder temp = ToolHelper.getTemporaryFolderForTest();

  @Test
  public void testBothBranchesDefaultConditionals() throws Exception {
    testBothBranches(false);
  }

  @Test
  public void testBothBranchesInvertConditionals() throws Exception {
    testBothBranches(true);
  }

  private void testBothBranches(boolean invertConditionals) throws Exception {
    Path testClassDir = temp.newFolder(TEST_PACKAGE.split(".")).toPath();
    Path testClassPath = testClassDir.resolve(TEST_CLASS + ".class");
    Path outputDexPath = temp.newFolder().toPath();

    Files.write(testClassPath, PreamblePositionTestSourceDump.dump());

    ToolHelper.runD8(
        D8Command.builder()
            .addProgramFiles(testClassPath)
            .setOutput(outputDexPath, OutputMode.DexIndexed)
            .setMode(CompilationMode.RELEASE),
        options -> {
          options.testing.invertConditionals = invertConditionals;
        });

    String fileName = TEST_CLASS + ".java";

    for (boolean testTrueBranch : new boolean[] {false, true}) {
      ArtCommandBuilder artCommandBuilder = new ArtCommandBuilder();
      artCommandBuilder.appendClasspath(outputDexPath.resolve("classes.dex").toString());
      artCommandBuilder.setMainClass(TEST_PACKAGE + "." + TEST_CLASS);
      if (!testTrueBranch) {
        artCommandBuilder.appendProgramArgument("1");
      }

      ProcessResult result = ToolHelper.runArtRaw(artCommandBuilder);

      assertNotEquals(result.exitCode, 0);
      if (testTrueBranch) {
        assertTrue(result.stderr.contains("<true-branch-exception>"));
        // Must have either explicit line = 0 or no line info at all.
        assertTrue(
            result.stderr.contains(fileName + ":0")
                || (result.stderr.contains("at " + TEST_PACKAGE + "." + TEST_CLASS + ".main")
                    && !result.stderr.contains(fileName + ":")));
      } else {
        assertTrue(result.stderr.contains("<false-branch-exception>"));
        assertTrue(
            result.stderr.contains(
                fileName + ":" + PreamblePositionTestSourceDump.FALSE_BRANCH_LINE_NUMBER));
      }
    }
  }
}
