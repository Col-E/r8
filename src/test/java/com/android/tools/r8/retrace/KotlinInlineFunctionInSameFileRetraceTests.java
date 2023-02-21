// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.retrace;

import static com.android.tools.r8.ToolHelper.getFilesInTestFolderRelativeToClass;
import static com.android.tools.r8.utils.codeinspector.Matchers.containsLinePositions;
import static com.android.tools.r8.utils.codeinspector.Matchers.isInlineFrame;
import static com.android.tools.r8.utils.codeinspector.Matchers.isInlineStack;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringContains.containsString;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion;
import com.android.tools.r8.KotlinTestBase;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.naming.retrace.StackTrace;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.Matchers.LinePosition;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KotlinInlineFunctionInSameFileRetraceTests extends KotlinTestBase {

  private static final String FILENAME_INLINE = "InlineFunctionsInSameFile.kt";
  private static final String MAIN = "retrace.InlineFunctionsInSameFileKt";

  private final TestParameters parameters;

  @Parameters(name = "{0}, target: {1}")
  public static List<Object[]> data() {
    // TODO(b/141817471): Extend with compilation modes.
    return buildParameters(
        getTestParameters()
            .withAllRuntimes()
            // TODO(b/186018416): Update to support tests retracing with PC mappings.
            .withApiLevelsEndingAtExcluding(apiLevelWithPcAsLineNumberSupport())
            .build(),
        getKotlinTestParameters().withAllCompilersAndTargetVersions().build());
  }

  public KotlinInlineFunctionInSameFileRetraceTests(
      TestParameters parameters, KotlinTestParameters kotlinParameters) {
    super(kotlinParameters);
    this.parameters = parameters;
  }

  private static final KotlinCompileMemoizer compilationResults =
      getCompileMemoizer(getKotlinSources());

  private static Collection<Path> getKotlinSources() {
    try {
      return getFilesInTestFolderRelativeToClass(
          KotlinInlineFunctionRetraceTest.class, "kt", ".kt");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private int getObfuscatedLinePosition() {
    return kotlinc.is(KotlinCompilerVersion.KOTLINC_1_3_72) ? 43 : 32;
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramFiles(compilationResults.getForConfiguration(kotlinc, targetVersion))
        .addRunClasspathFiles(buildOnDexRuntime(parameters, kotlinc.getKotlinStdlibJar()))
        .run(parameters.getRuntime(), MAIN)
        .assertFailureWithErrorThatMatches(containsString("foo"))
        .assertFailureWithErrorThatMatches(
            containsString(
                "at retrace.InlineFunctionsInSameFileKt.main(InlineFunctionsInSameFile.kt:"
                    + getObfuscatedLinePosition()));
  }

  @Test
  public void testRetraceKotlinInlineStaticFunction() throws Exception {
    Path kotlinSources = compilationResults.getForConfiguration(kotlinc, targetVersion);
    CodeInspector kotlinInspector = new CodeInspector(kotlinSources);
    testForR8(parameters.getBackend())
        .addProgramFiles(compilationResults.getForConfiguration(kotlinc, targetVersion))
        .addProgramFiles(kotlinc.getKotlinStdlibJar(), kotlinc.getKotlinAnnotationJar())
        .addKeepAttributes("SourceFile", "LineNumberTable")
        .setMode(CompilationMode.RELEASE)
        .addKeepMainRule(MAIN)
        .allowDiagnosticWarningMessages()
        .addDontObfuscate()
        .setMinApi(parameters)
        .compile()
        .assertAllWarningMessagesMatch(equalTo("Resource 'META-INF/MANIFEST.MF' already exists."))
        .run(parameters.getRuntime(), MAIN)
        .assertFailureWithErrorThatMatches(containsString("main"))
        .inspectStackTrace(
            (stackTrace, codeInspector) -> {
              MethodSubject mainSubject =
                  codeInspector.clazz(MAIN).uniqueMethodWithOriginalName("main");
              LinePosition inlineStack =
                  LinePosition.stack(
                      LinePosition.create(
                          kotlinInspector
                              .clazz("retrace.InlineFunctionsInSameFileKt")
                              .uniqueMethodWithOriginalName("foo")
                              .asFoundMethodSubject(),
                          1,
                          8,
                          FILENAME_INLINE),
                      LinePosition.create(
                          mainSubject.asFoundMethodSubject(), 1, 21, FILENAME_INLINE));
              checkInlineInformation(stackTrace, codeInspector, mainSubject, inlineStack);
            });
  }

  private void checkInlineInformation(
      StackTrace stackTrace,
      CodeInspector codeInspector,
      MethodSubject mainSubject,
      LinePosition inlineStack) {
    assertThat(mainSubject, isPresent());
    RetraceFrameResult retraceResult =
        ListUtils.last(
                mainSubject
                    .streamInstructions()
                    .filter(InstructionSubject::isThrow)
                    .collect(Collectors.toList()))
            .retraceLinePosition(codeInspector.retrace());
    assertThat(retraceResult, isInlineFrame());
    assertThat(retraceResult, isInlineStack(inlineStack));
    assertThat(stackTrace, containsLinePositions(inlineStack));
  }
}
