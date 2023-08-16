// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.determinism;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.dump.CompilerDump;
import com.android.tools.r8.dump.ProguardConfigSanitizer;
import com.android.tools.r8.utils.DeterminismChecker;
import com.google.common.collect.ImmutableList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class AppDeterminismTest extends TestBase {

  @Parameterized.Parameters(name = "{1}")
  public static List<Object[]> data() {
    return buildParameters(getTestParameters().withNoneRuntime().build(), ImmutableList.of("tivi"));
  }

  private final String app;
  private final int iterations = 2;

  public AppDeterminismTest(TestParameters parameters, String app) {
    parameters.assertNoneRuntime();
    this.app = app;
  }

  @Test
  public void test() throws Exception {
    CompilerDump dump =
        CompilerDump.fromArchive(
            Paths.get(ToolHelper.THIRD_PARTY_DIR, "opensource-apps", app, "dump_app.zip"),
            temp.newFolder().toPath());
    Path logDirectory = temp.newFolder().toPath();
    Path ref = compile(1, dump, logDirectory);
    for (int i = 2; i <= iterations; i++) {
      Path next = compile(i, dump, logDirectory);
      assertProgramsEqual(ref, next);
    }
    // Check that setting the determinism checker wrote a log file.
    assertTrue(Files.exists(logDirectory.resolve("0.log")));
  }

  private Path compile(int iteration, CompilerDump dump, Path logDirectory) throws Exception {
    System.out.println("= compiling " + iteration + "/" + iterations + " ======================");
    return testForR8(Backend.DEX)
        .allowStdoutMessages()
        .allowStderrMessages()
        .addProgramFiles(dump.getProgramArchive())
        .addLibraryFiles(dump.getLibraryArchive())
        .apply(
            b ->
                dump.sanitizeProguardConfig(
                    ProguardConfigSanitizer.createDefaultForward(b::addKeepRules)
                        .onPrintDirective(
                            directive -> System.out.println("Stripping directive: " + directive))))
        .allowUnusedDontWarnPatterns()
        .allowUnusedProguardConfigurationRules()
        .allowUnnecessaryDontWarnWildcards()
        .allowDiagnosticMessages()
        // TODO(b/222228826): Disallow open interfaces.
        .addOptionsModification(
            options -> options.getOpenClosedInterfacesOptions().suppressAllOpenInterfaces())
        .addOptionsModification(
            options ->
                options.testing.setDeterminismChecker(
                    DeterminismChecker.createWithFileBacking(logDirectory)))
        .compile()
        .writeToZip();
  }
}
