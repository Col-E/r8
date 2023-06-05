// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static com.android.tools.r8.ToolHelper.EXAMPLES_BUILD_DIR;
import static com.android.tools.r8.ToolHelper.EXAMPLES_DIR;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.diagnostic.MissingDefinitionsDiagnostic;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TreeShakingSpecificTest extends TestBase {

  private Backend backend;
  private AndroidApiLevel minApi = AndroidApiLevel.LATEST;

  @Parameters(name = "Backend: {1}")
  public static List<Object[]> data() {
    return buildParameters(TestParameters.builder().withNoneRuntime().build(), Backend.values());
  }

  public TreeShakingSpecificTest(TestParameters parameters, Backend backend) {
    this.backend = backend;
    parameters.assertNoneRuntime();
  }

  private Path getProgramFiles(String test) {
    return Paths.get(EXAMPLES_BUILD_DIR, test + ".jar");
  }

  private byte[] getProgramDexFileData(String test) throws IOException {
    return Files.readAllBytes(Paths.get(EXAMPLES_BUILD_DIR, test, "classes.dex"));
  }

  @Test
  public void testIgnoreWarnings() throws Exception {
    // Generate R8 processed version without library option.
    String test = "shaking2";
    testForR8(backend)
        .addProgramFiles(getProgramFiles(test))
        .addKeepRuleFiles(Paths.get(EXAMPLES_DIR, test, "keep-rules.txt"))
        .addIgnoreWarnings()
        .setMinApi(minApi)
        .compile();
  }

  @Test(expected = CompilationFailedException.class)
  public void testMissingLibrary() throws Exception {
    // Generate R8 processed version without library option.
    String test = "shaking2";
    testForR8(backend)
        .addProgramFiles(getProgramFiles(test))
        .addLibraryFiles()
        .addKeepRuleFiles(Paths.get(EXAMPLES_DIR, test, "keep-rules.txt"))
        .allowDiagnosticErrorMessages()
        .setMinApi(minApi)
        .compileWithExpectedDiagnostics(
            diagnostics -> {
              diagnostics
                  .assertOnlyErrors()
                  .assertErrorsMatch(diagnosticType(MissingDefinitionsDiagnostic.class));

              MissingDefinitionsDiagnostic diagnostic =
                  (MissingDefinitionsDiagnostic) diagnostics.getErrors().get(0);
              assertThat(
                  diagnostic.getDiagnosticMessage(),
                  allOf(
                      containsString("Missing class java.io.PrintStream"),
                      containsString("Missing class java.lang.Object"),
                      containsString("Missing class java.lang.String"),
                      containsString("Missing class java.lang.StringBuilder"),
                      containsString("Missing class java.lang.System")));
            });
  }

  @Test
  public void testPrintMapping() throws Throwable {
    // Generate R8 processed version without library option.
    String test = "shaking1";
    testForR8(backend)
        .addProgramFiles(getProgramFiles(test))
        .addKeepRuleFiles(Paths.get(EXAMPLES_DIR, test, "keep-rules.txt"))
        .addOptionsModification(options -> options.inlinerOptions().enableInlining = false)
        .setMinApi(minApi)
        .compile()
        .inspectProguardMap(
            proguardMap -> {
              // Remove comments.
              String actualMapping =
                  Stream.of(proguardMap.split("\n"))
                      .filter(line -> !line.startsWith("#"))
                      .collect(Collectors.joining("\n"));
              String refMapping =
                  new String(
                      Files.readAllBytes(
                          Paths.get(
                              EXAMPLES_DIR,
                              "shaking1",
                              "print-mapping-" + StringUtils.toLowerCase(backend.name()) + ".ref")),
                      StandardCharsets.UTF_8);
              assertEquals(sorted(refMapping), sorted(actualMapping));
            });
  }

  private static String sorted(String str) {
    return new BufferedReader(new StringReader(str))
        .lines().sorted().filter(s -> !s.isEmpty()).collect(Collectors.joining("\n"));
  }
}
