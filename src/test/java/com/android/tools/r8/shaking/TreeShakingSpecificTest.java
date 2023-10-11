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
import com.android.tools.r8.mappingcompose.ComposeTestHelpers;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
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

  private String getExpectedCf() {
    return StringUtils.lines(
        "shaking1.Shaking -> shaking1.Shaking:",
        "# {'id':'sourceFile','fileName':'Shaking.java'}",
        "    1:2:void main(java.lang.String[]):8:9 -> main",
        "shaking1.Used -> a.a:",
        "# {'id':'sourceFile','fileName':'Used.java'}",
        "    java.lang.String name -> a",
        "    1:14:void <init>(java.lang.String):0:13 -> <init>",
        "    1:1:java.lang.String method():17:17 -> a",
        "    1:1:java.lang.String aMethodThatIsNotUsedButKept():21:21 "
            + "-> aMethodThatIsNotUsedButKept");
  }

  private String getExpectedDex() {
    return StringUtils.lines(
        "shaking1.Shaking -> shaking1.Shaking:",
        "# {'id':'sourceFile','fileName':'Shaking.java'}",
        "    0:6:void main(java.lang.String[]):8:8 -> main",
        "    7:21:void main(java.lang.String[]):9:9 -> main",
        "shaking1.Used -> a.a:",
        "# {'id':'sourceFile','fileName':'Used.java'}",
        "    java.lang.String name -> a",
        "    0:2:void <init>(java.lang.String):12:12 -> <init>",
        "    3:5:void <init>(java.lang.String):13:13 -> <init>",
        "    0:16:java.lang.String method():17:17 -> a",
        "    0:2:java.lang.String aMethodThatIsNotUsedButKept():21:21 "
            + "-> aMethodThatIsNotUsedButKept");
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
              // Remove header.
              List<String> lines = StringUtils.splitLines(proguardMap);
              int firstNonHeaderLine = 0;
              for (String line : lines) {
                if (line.startsWith("#")) {
                  firstNonHeaderLine++;
                } else {
                  break;
                }
              }
              assertEquals(
                  backend.isCf() ? getExpectedCf() : getExpectedDex(),
                  ComposeTestHelpers.doubleToSingleQuote(
                      StringUtils.lines(lines.subList(firstNonHeaderLine, lines.size()))));
            });
  }
}
