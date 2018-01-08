// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import static com.android.tools.r8.ToolHelper.EXAMPLES_BUILD_DIR;
import static com.android.tools.r8.ToolHelper.EXAMPLES_DIR;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.ToolHelper;
import java.io.BufferedReader;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

public class TreeShakingSpecificTest {
  private static final String VALID_PROGUARD_DIR = "src/test/proguard/valid/";

  @Rule
  public TemporaryFolder temp = ToolHelper.getTemporaryFolderForTest();

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void testIgnoreWarnings() throws Exception {
    // Generate R8 processed version without library option.
    Path out = temp.getRoot().toPath();
    String test = "shaking2";
    Path originalDex = Paths.get(EXAMPLES_BUILD_DIR, test, "classes.dex");
    Path keepRules = Paths.get(EXAMPLES_DIR, test, "keep-rules.txt");
    Path ignoreWarnings = Paths.get(VALID_PROGUARD_DIR, "ignorewarnings.flags");
    R8Command.Builder builder = R8Command.builder()
        .setOutput(out, OutputMode.DexIndexed)
        .addProguardConfigurationFiles(keepRules, ignoreWarnings);
    ToolHelper.getAppBuilder(builder).addProgramFiles(originalDex);
    R8.run(builder.build());
  }

  @Test(expected = CompilationFailedException.class)
  public void testMissingLibrary() throws Exception {
    // Generate R8 processed version without library option.
    Path out = temp.getRoot().toPath();
    String test = "shaking2";
    Path originalDex = Paths.get(EXAMPLES_BUILD_DIR, test, "classes.dex");
    Path keepRules = Paths.get(EXAMPLES_DIR, test, "keep-rules.txt");
    DiagnosticsHandler handler = new DiagnosticsHandler() {
      @Override
      public void error(Diagnostic error) {
        if (!error.getDiagnosticMessage().contains("library classes are missing")) {
          throw new RuntimeException("Unexpected compilation error");
        }
      }
    };
    R8Command.Builder builder = R8Command.builder(handler)
        .setOutput(out, OutputMode.DexIndexed)
        .addProguardConfigurationFiles(keepRules);
    ToolHelper.getAppBuilder(builder).addProgramFiles(originalDex);
    R8.run(builder.build());
  }

  @Test
  public void testPrintMapping() throws Exception {
    // Generate R8 processed version without library option.
    String test = "shaking1";
    Path out = temp.getRoot().toPath();
    Path originalDex = Paths.get(EXAMPLES_BUILD_DIR, test, "classes.dex");
    Path keepRules = Paths.get(EXAMPLES_DIR, test, "keep-rules.txt");

    // Create a flags file in temp dir requesting dump of the mapping.
    // The mapping file will be created alongside the flags file in temp dir.
    Path printMapping = out.resolve("printmapping.flags");
    try (PrintStream mapping = new PrintStream(printMapping.toFile())) {
      mapping.println("-printmapping mapping.txt");
    }

    R8Command.Builder builder = R8Command.builder()
        .setOutput(out, OutputMode.DexIndexed)
        .addProguardConfigurationFiles(keepRules, printMapping);
    ToolHelper.getAppBuilder(builder).addProgramFiles(originalDex);
    // Turn off inlining, as we want the mapping that is printed to be stable.
    ToolHelper.runR8(builder.build(), options -> options.inlineAccessors = false);

    Path outputmapping = out.resolve("mapping.txt");
    String actualMapping;
    actualMapping = new String(Files.readAllBytes(outputmapping), StandardCharsets.UTF_8);
    String refMapping = new String(Files.readAllBytes(
        Paths.get(EXAMPLES_DIR, "shaking1", "print-mapping.ref")), StandardCharsets.UTF_8);
    Assert.assertEquals(sorted(refMapping), sorted(actualMapping));
  }

  private static String sorted(String str) {
    return new BufferedReader(new StringReader(str))
        .lines().sorted().filter(s -> !s.isEmpty()).collect(Collectors.joining("\n"));
  }
}
