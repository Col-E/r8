// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.maindexlist;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.OutputMode;
import com.android.tools.r8.utils.StringDiagnostic;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class MainDexListOutputTest extends TestBase {

  class Reporter implements DiagnosticsHandler {
    int errorCount = 0;

    @Override
    public void error(Diagnostic error) {
      errorCount++;
      assertTrue(error instanceof StringDiagnostic);
      assertTrue(error.getDiagnosticMessage().contains("main-dex"));
    }
  }

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void testNoMainDex() throws Exception {
    Reporter reporter = new Reporter();
    try {
      Path mainDexListOutput = temp.getRoot().toPath().resolve("main-dex-output.txt");
      R8Command command =
          ToolHelper.prepareR8CommandBuilder(readClasses(HelloWorldMain.class), reporter)
              .setMainDexListOutputPath(mainDexListOutput)
              .build();
    } catch (CompilationFailedException e) {
      assertEquals(1, reporter.errorCount);
      return;
    }
    fail("Expected CompilationFailedException");
  }

  @Test
  public void testWithMainDex() throws Exception {
    Path mainDexRules = writeTextToTempFile(keepMainProguardConfiguration(HelloWorldMain.class));
    Path mainDexListOutput = temp.getRoot().toPath().resolve("main-dex-output.txt");
    R8Command command =
        ToolHelper.prepareR8CommandBuilder(readClasses(HelloWorldMain.class))
            .addMainDexRulesFiles(mainDexRules)
            .setMainDexListOutputPath(mainDexListOutput)
            .setOutput(temp.getRoot().toPath(), OutputMode.DexIndexed)
            .build();
    ToolHelper.runR8(command);
    // Main dex list with the single class.
    assertEquals(
        ImmutableList.of(HelloWorldMain.class.getTypeName().replace('.', '/') + ".class"),
        FileUtils.readTextFile(mainDexListOutput)
            .stream()
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList()));
  }
}
