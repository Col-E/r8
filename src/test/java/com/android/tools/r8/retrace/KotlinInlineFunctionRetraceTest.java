// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import static com.android.tools.r8.Collectors.toSingle;
import static com.android.tools.r8.KotlinCompilerTool.KOTLINC;
import static com.android.tools.r8.ToolHelper.getFilesInTestFolderRelativeToClass;
import static com.android.tools.r8.utils.codeinspector.Matchers.containsLinePositions;
import static com.android.tools.r8.utils.codeinspector.Matchers.isInlineFrame;
import static com.android.tools.r8.utils.codeinspector.Matchers.isInlineStack;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringContains.containsString;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.TestRuntime.CfRuntime;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import com.android.tools.r8.naming.retrace.StackTrace;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.Matchers.LinePosition;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KotlinInlineFunctionRetraceTest extends TestBase {

  private final TestParameters parameters;
  // TODO(b/151132660): Fix filename
  private static final String FILENAME_INLINE_STATIC = "InlineFunctionKt.kt";
  private static final String FILENAME_INLINE_INSTANCE = "InlineFunction.kt";

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    // TODO(b/141817471): Extend with compilation modes.
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public KotlinInlineFunctionRetraceTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private static Function<TestRuntime, Path> compilationResults =
      memoizeFunction(KotlinInlineFunctionRetraceTest::compileKotlinCode);

  private static Path compileKotlinCode(TestRuntime runtime) throws IOException {
    CfRuntime cfRuntime = runtime.isCf() ? runtime.asCf() : TestRuntime.getCheckedInJdk9();
    return kotlinc(cfRuntime, getStaticTemp(), KOTLINC, KotlinTargetVersion.JAVA_8)
        .addSourceFiles(
            getFilesInTestFolderRelativeToClass(KotlinInlineFunctionRetraceTest.class, "kt", ".kt"))
        .compile();
  }

  private FoundMethodSubject inlineExceptionStatic(CodeInspector kotlinInspector) {
    return kotlinInspector
        .clazz("retrace.InlineFunctionKt")
        .uniqueMethodWithName("inlineExceptionStatic")
        .asFoundMethodSubject();
  }

  private FoundMethodSubject inlineExceptionInstance(CodeInspector kotlinInspector) {
    return kotlinInspector
        .clazz("retrace.InlineFunction")
        .uniqueMethodWithName("inlineExceptionInstance")
        .asFoundMethodSubject();
  }

  @Test
  public void testRuntime() throws ExecutionException, CompilationFailedException, IOException {
    testForRuntime(parameters)
        .addProgramFiles(compilationResults.apply(parameters.getRuntime()))
        .addRunClasspathFiles(buildOnDexRuntime(parameters, ToolHelper.getKotlinStdlibJar()))
        .run(parameters.getRuntime(), "retrace.MainKt")
        .assertFailureWithErrorThatMatches(containsString("inlineExceptionStatic"))
        .assertFailureWithErrorThatMatches(containsString("at retrace.MainKt.main(Main.kt:15)"));
  }

  @Test
  public void testRetraceKotlinInlineStaticFunction()
      throws ExecutionException, CompilationFailedException, IOException {
    String main = "retrace.MainKt";
    String mainFileName = "Main.kt";
    Path kotlinSources = compilationResults.apply(parameters.getRuntime());
    CodeInspector kotlinInspector = new CodeInspector(kotlinSources);
    testForR8(parameters.getBackend())
        .addProgramFiles(kotlinSources, ToolHelper.getKotlinStdlibJar())
        .addKeepAttributes("SourceFile", "LineNumberTable")
        .allowDiagnosticWarningMessages()
        .setMode(CompilationMode.RELEASE)
        .addKeepMainRule(main)
        .setMinApi(parameters.getApiLevel())
        .compile()
        .assertAllWarningMessagesMatch(equalTo("Resource 'META-INF/MANIFEST.MF' already exists."))
        .run(parameters.getRuntime(), main)
        .assertFailureWithErrorThatMatches(containsString("inlineExceptionStatic"))
        .inspectStackTrace(
            (stackTrace, codeInspector) -> {
              MethodSubject mainSubject = codeInspector.clazz(main).uniqueMethodWithName("main");
              LinePosition inlineStack =
                  LinePosition.stack(
                      LinePosition.create(
                          inlineExceptionStatic(kotlinInspector), 2, 8, FILENAME_INLINE_STATIC),
                      LinePosition.create(mainSubject.asFoundMethodSubject(), 2, 15, mainFileName));
              checkInlineInformation(stackTrace, codeInspector, mainSubject, inlineStack);
            });
  }

  @Test
  public void testRetraceKotlinInlineInstanceFunction()
      throws ExecutionException, CompilationFailedException, IOException {
    String main = "retrace.MainInstanceKt";
    String mainFileName = "MainInstance.kt";
    Path kotlinSources = compilationResults.apply(parameters.getRuntime());
    CodeInspector kotlinInspector = new CodeInspector(kotlinSources);
    testForR8(parameters.getBackend())
        .addProgramFiles(kotlinSources, ToolHelper.getKotlinStdlibJar())
        .addKeepAttributes("SourceFile", "LineNumberTable")
        .allowDiagnosticWarningMessages()
        .setMode(CompilationMode.RELEASE)
        .addKeepMainRule(main)
        .setMinApi(parameters.getApiLevel())
        .compile()
        .assertAllWarningMessagesMatch(equalTo("Resource 'META-INF/MANIFEST.MF' already exists."))
        .run(parameters.getRuntime(), main)
        .assertFailureWithErrorThatMatches(containsString("inlineExceptionInstance"))
        .inspectStackTrace(
            (stackTrace, codeInspector) -> {
              MethodSubject mainSubject = codeInspector.clazz(main).uniqueMethodWithName("main");
              LinePosition inlineStack =
                  LinePosition.stack(
                      LinePosition.create(
                          inlineExceptionInstance(kotlinInspector),
                          2,
                          15,
                          FILENAME_INLINE_INSTANCE),
                      LinePosition.create(mainSubject.asFoundMethodSubject(), 2, 13, mainFileName));
              checkInlineInformation(stackTrace, codeInspector, mainSubject, inlineStack);
            });
  }

  @Test
  public void testRetraceKotlinNestedInlineFunction()
      throws ExecutionException, CompilationFailedException, IOException {
    String main = "retrace.MainNestedKt";
    String mainFileName = "MainNested.kt";
    Path kotlinSources = compilationResults.apply(parameters.getRuntime());
    CodeInspector kotlinInspector = new CodeInspector(kotlinSources);
    testForR8(parameters.getBackend())
        .addProgramFiles(kotlinSources, ToolHelper.getKotlinStdlibJar())
        .addKeepAttributes("SourceFile", "LineNumberTable")
        .allowDiagnosticWarningMessages()
        .setMode(CompilationMode.RELEASE)
        .addKeepMainRule(main)
        .setMinApi(parameters.getApiLevel())
        .compile()
        .assertAllWarningMessagesMatch(equalTo("Resource 'META-INF/MANIFEST.MF' already exists."))
        .run(parameters.getRuntime(), main)
        .assertFailureWithErrorThatMatches(containsString("inlineExceptionStatic"))
        .inspectStackTrace(
            (stackTrace, codeInspector) -> {
              MethodSubject mainSubject = codeInspector.clazz(main).uniqueMethodWithName("main");
              LinePosition inlineStack =
                  LinePosition.stack(
                      LinePosition.create(
                          inlineExceptionStatic(kotlinInspector), 3, 8, FILENAME_INLINE_STATIC),
                      // TODO(b/146399675): There should be a nested frame on
                      //  retrace.NestedInlineFunctionKt.nestedInline(line 10).
                      LinePosition.create(mainSubject.asFoundMethodSubject(), 3, 19, mainFileName));
              checkInlineInformation(stackTrace, codeInspector, mainSubject, inlineStack);
            });
  }

  @Test
  public void testRetraceKotlinNestedInlineFunctionOnFirstLine()
      throws ExecutionException, CompilationFailedException, IOException {
    String main = "retrace.MainNestedFirstLineKt";
    String mainFileName = "MainNestedFirstLine.kt";
    Path kotlinSources = compilationResults.apply(parameters.getRuntime());
    CodeInspector kotlinInspector = new CodeInspector(kotlinSources);
    testForR8(parameters.getBackend())
        .addProgramFiles(kotlinSources, ToolHelper.getKotlinStdlibJar())
        .addKeepAttributes("SourceFile", "LineNumberTable")
        .allowDiagnosticWarningMessages()
        .setMode(CompilationMode.RELEASE)
        .addKeepMainRule(main)
        .setMinApi(parameters.getApiLevel())
        .compile()
        .assertAllWarningMessagesMatch(equalTo("Resource 'META-INF/MANIFEST.MF' already exists."))
        .run(parameters.getRuntime(), main)
        .assertFailureWithErrorThatMatches(containsString("inlineExceptionStatic"))
        .inspectStackTrace(
            (stackTrace, codeInspector) -> {
              MethodSubject mainSubject = codeInspector.clazz(main).uniqueMethodWithName("main");
              LinePosition inlineStack =
                  LinePosition.stack(
                      LinePosition.create(
                          inlineExceptionStatic(kotlinInspector), 2, 8, FILENAME_INLINE_STATIC),
                      // TODO(b/146399675): There should be a nested frame on
                      //  retrace.NestedInlineFunctionKt.nestedInlineOnFirstLine(line 15).
                      LinePosition.create(mainSubject.asFoundMethodSubject(), 2, 20, mainFileName));
              checkInlineInformation(stackTrace, codeInspector, mainSubject, inlineStack);
            });
  }

  private void checkInlineInformation(
      StackTrace stackTrace,
      CodeInspector codeInspector,
      MethodSubject mainSubject,
      LinePosition inlineStack) {
    assertThat(mainSubject, isPresent());
    RetraceMethodResult retraceResult =
        mainSubject
            .streamInstructions()
            .filter(InstructionSubject::isThrow)
            .collect(toSingle())
            .retraceLinePosition(codeInspector.retrace());
    assertThat(retraceResult, isInlineFrame());
    assertThat(retraceResult, isInlineStack(inlineStack));
    assertThat(stackTrace, containsLinePositions(inlineStack));
  }
}
