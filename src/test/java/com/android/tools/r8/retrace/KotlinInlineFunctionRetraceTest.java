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
import com.android.tools.r8.KotlinTestBase;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.naming.retrace.StackTrace;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
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
public class KotlinInlineFunctionRetraceTest extends KotlinTestBase {

  private final TestParameters parameters;
  private static final String FILENAME_INLINE_STATIC = "InlineFunction.kt";
  private static final String FILENAME_INLINE_INSTANCE = "InlineFunction.kt";

  @Parameters(name = "{0}, {1}")
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

  public KotlinInlineFunctionRetraceTest(
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

  private FoundMethodSubject inlineExceptionStatic(CodeInspector kotlinInspector) {
    return kotlinInspector
        .clazz("retrace.InlineFunctionKt")
        .uniqueMethodWithOriginalName("inlineExceptionStatic")
        .asFoundMethodSubject();
  }

  private FoundMethodSubject inlineExceptionInstance(CodeInspector kotlinInspector) {
    return kotlinInspector
        .clazz("retrace.InlineFunction")
        .uniqueMethodWithOriginalName("inlineExceptionInstance")
        .asFoundMethodSubject();
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramFiles(compilationResults.getForConfiguration(kotlinc, targetVersion))
        .addRunClasspathFiles(buildOnDexRuntime(parameters, kotlinc.getKotlinStdlibJar()))
        .run(parameters.getRuntime(), "retrace.MainKt")
        .assertFailureWithErrorThatMatches(containsString("inlineExceptionStatic"))
        .assertFailureWithErrorThatMatches(containsString("at retrace.MainKt.main(Main.kt:15)"));
  }

  @Test
  public void testRetraceKotlinInlineStaticFunction() throws Exception {
    String main = "retrace.MainKt";
    String mainFileName = "Main.kt";
    Path kotlinSources = compilationResults.getForConfiguration(kotlinc, targetVersion);
    CodeInspector kotlinInspector = new CodeInspector(kotlinSources);
    testForR8(parameters.getBackend())
        .addProgramFiles(
            kotlinSources, kotlinc.getKotlinStdlibJar(), kotlinc.getKotlinAnnotationJar())
        .addKeepAttributes("SourceFile", "LineNumberTable")
        .allowDiagnosticWarningMessages()
        .setMode(CompilationMode.RELEASE)
        .addKeepMainRule(main)
        .setMinApi(parameters)
        .compile()
        .assertAllWarningMessagesMatch(equalTo("Resource 'META-INF/MANIFEST.MF' already exists."))
        .run(parameters.getRuntime(), main)
        .assertFailureWithErrorThatMatches(containsString("inlineExceptionStatic"))
        .inspectStackTrace(
            (stackTrace, codeInspector) -> {
              MethodSubject mainSubject =
                  codeInspector.clazz(main).uniqueMethodWithOriginalName("main");
              LinePosition inlineStack =
                  LinePosition.stack(
                      LinePosition.create(
                          inlineExceptionStatic(kotlinInspector), 2, 8, FILENAME_INLINE_STATIC),
                      LinePosition.create(mainSubject.asFoundMethodSubject(), 2, 9, mainFileName));
              checkInlineInformation(stackTrace, codeInspector, mainSubject, inlineStack);
            });
  }

  @Test
  public void testRetraceKotlinInlineInstanceFunction() throws Exception {
    String main = "retrace.MainInstanceKt";
    String mainFileName = "MainInstance.kt";
    Path kotlinSources = compilationResults.getForConfiguration(kotlinc, targetVersion);
    CodeInspector kotlinInspector = new CodeInspector(kotlinSources);
    testForR8(parameters.getBackend())
        .addProgramFiles(
            kotlinSources, kotlinc.getKotlinStdlibJar(), kotlinc.getKotlinAnnotationJar())
        .addKeepAttributes("SourceFile", "LineNumberTable")
        .allowDiagnosticWarningMessages()
        .setMode(CompilationMode.RELEASE)
        .addKeepMainRule(main)
        .setMinApi(parameters)
        .compile()
        .assertAllWarningMessagesMatch(equalTo("Resource 'META-INF/MANIFEST.MF' already exists."))
        .run(parameters.getRuntime(), main)
        .assertFailureWithErrorThatMatches(containsString("inlineExceptionInstance"))
        .inspectStackTrace(
            (stackTrace, codeInspector) -> {
              MethodSubject mainSubject =
                  codeInspector.clazz(main).uniqueMethodWithOriginalName("main");
              LinePosition inlineStack =
                  LinePosition.stack(
                      LinePosition.create(
                          inlineExceptionInstance(kotlinInspector),
                          2,
                          15,
                          FILENAME_INLINE_INSTANCE),
                      LinePosition.create(mainSubject.asFoundMethodSubject(), 2, 7, mainFileName));
              checkInlineInformation(stackTrace, codeInspector, mainSubject, inlineStack);
            });
  }

  @Test
  public void testRetraceKotlinNestedInlineFunction() throws Exception {
    String main = "retrace.MainNestedKt";
    String mainFileName = "MainNested.kt";
    Path kotlinSources = compilationResults.getForConfiguration(kotlinc, targetVersion);
    CodeInspector kotlinInspector = new CodeInspector(kotlinSources);
    testForR8(parameters.getBackend())
        .addProgramFiles(
            kotlinSources, kotlinc.getKotlinStdlibJar(), kotlinc.getKotlinAnnotationJar())
        .addKeepAttributes("SourceFile", "LineNumberTable")
        .allowDiagnosticWarningMessages()
        .setMode(CompilationMode.RELEASE)
        .addKeepMainRule(main)
        .setMinApi(parameters)
        .compile()
        .assertAllWarningMessagesMatch(equalTo("Resource 'META-INF/MANIFEST.MF' already exists."))
        .run(parameters.getRuntime(), main)
        .assertFailureWithErrorThatMatches(containsString("inlineExceptionStatic"))
        .inspectStackTrace(
            (stackTrace, codeInspector) -> {
              MethodSubject mainSubject =
                  codeInspector.clazz(main).uniqueMethodWithOriginalName("main");
              LinePosition inlineStack =
                  LinePosition.stack(
                      LinePosition.create(
                          inlineExceptionStatic(kotlinInspector), 3, 8, FILENAME_INLINE_STATIC),
                      // TODO(b/146399675): There should be a nested frame on
                      //  retrace.NestedInlineFunctionKt.nestedInline(line 10).
                      LinePosition.create(mainSubject.asFoundMethodSubject(), 3, 10, mainFileName));
              checkInlineInformation(stackTrace, codeInspector, mainSubject, inlineStack);
            });
  }

  @Test
  public void testRetraceKotlinNestedInlineFunctionOnFirstLine() throws Exception {
    String main = "retrace.MainNestedFirstLineKt";
    String mainFileName = "MainNestedFirstLine.kt";
    Path kotlinSources = compilationResults.getForConfiguration(kotlinc, targetVersion);
    CodeInspector kotlinInspector = new CodeInspector(kotlinSources);
    testForR8(parameters.getBackend())
        .addProgramFiles(
            kotlinSources, kotlinc.getKotlinStdlibJar(), kotlinc.getKotlinAnnotationJar())
        .addKeepAttributes("SourceFile", "LineNumberTable")
        .allowDiagnosticWarningMessages()
        .setMode(CompilationMode.RELEASE)
        .addKeepMainRule(main)
        .setMinApi(parameters)
        .compile()
        .assertAllWarningMessagesMatch(equalTo("Resource 'META-INF/MANIFEST.MF' already exists."))
        .run(parameters.getRuntime(), main)
        .assertFailureWithErrorThatMatches(containsString("inlineExceptionStatic"))
        .inspectStackTrace(
            (stackTrace, codeInspector) -> {
              MethodSubject mainSubject =
                  codeInspector.clazz(main).uniqueMethodWithOriginalName("main");
              LinePosition inlineStack =
                  LinePosition.stack(
                      LinePosition.create(
                          inlineExceptionStatic(kotlinInspector), 2, 8, FILENAME_INLINE_STATIC),
                      // TODO(b/146399675): There should be a nested frame on
                      //  retrace.NestedInlineFunctionKt.nestedInline(line 10).
                      LinePosition.create(mainSubject.asFoundMethodSubject(), 2, 10, mainFileName));
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
