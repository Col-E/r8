// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import static com.android.tools.r8.Collectors.toSingle;
import static com.android.tools.r8.KotlinCompilerTool.KOTLINC;
import static com.android.tools.r8.ToolHelper.getFilesInTestFolderRelativeToClass;
import static com.android.tools.r8.utils.codeinspector.Matchers.containsInlinePosition;
import static com.android.tools.r8.utils.codeinspector.Matchers.isInlineFrame;
import static com.android.tools.r8.utils.codeinspector.Matchers.isInlineStack;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
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
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.Matchers.InlinePosition;
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
    testForR8(parameters.getBackend())
        .addProgramFiles(compilationResults.apply(parameters.getRuntime()))
        .addProgramFiles(ToolHelper.getKotlinStdlibJar())
        .addKeepAttributes("SourceFile", "LineNumberTable")
        .setMode(CompilationMode.RELEASE)
        .addKeepMainRule(main)
        .addOptionsModification(
            internalOptions -> internalOptions.enableSourceDebugExtensionRewriter = true)
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), main)
        .assertFailureWithErrorThatMatches(containsString("inlineExceptionStatic"))
        .inspectStackTrace(
            (stackTrace, codeInspector) -> {
              MethodSubject mainSubject = codeInspector.clazz(main).uniqueMethodWithName("main");
              InlinePosition inlineStack =
                  InlinePosition.stack(
                      InlinePosition.create(
                          "retrace.InlineFunctionKt", "inlineExceptionStatic", 2, 8),
                      InlinePosition.create(mainSubject.asFoundMethodSubject(), 2, 15));
              checkInlineInformation(stackTrace, codeInspector, mainSubject, inlineStack);
            });
  }

  @Test
  public void testRetraceKotlinInlineInstanceFunction()
      throws ExecutionException, CompilationFailedException, IOException {
    String main = "retrace.MainInstanceKt";
    testForR8(parameters.getBackend())
        .addProgramFiles(compilationResults.apply(parameters.getRuntime()))
        .addProgramFiles(ToolHelper.getKotlinStdlibJar())
        .addKeepAttributes("SourceFile", "LineNumberTable")
        .setMode(CompilationMode.RELEASE)
        .addKeepMainRule(main)
        .addOptionsModification(
            internalOptions -> internalOptions.enableSourceDebugExtensionRewriter = true)
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), main)
        .assertFailureWithErrorThatMatches(containsString("inlineExceptionInstance"))
        .inspectStackTrace(
            (stackTrace, codeInspector) -> {
              MethodSubject mainSubject = codeInspector.clazz(main).uniqueMethodWithName("main");
              InlinePosition inlineStack =
                  InlinePosition.stack(
                      InlinePosition.create(
                          "retrace.InlineFunction", "inlineExceptionInstance", 2, 15),
                      InlinePosition.create(mainSubject.asFoundMethodSubject(), 2, 13));
              checkInlineInformation(stackTrace, codeInspector, mainSubject, inlineStack);
            });
  }

  @Test
  public void testRetraceKotlinNestedInlineFunction()
      throws ExecutionException, CompilationFailedException, IOException {
    String main = "retrace.MainNestedKt";
    testForR8(parameters.getBackend())
        .addProgramFiles(compilationResults.apply(parameters.getRuntime()))
        .addProgramFiles(ToolHelper.getKotlinStdlibJar())
        .addKeepAttributes("SourceFile", "LineNumberTable")
        .setMode(CompilationMode.RELEASE)
        .addKeepMainRule(main)
        .addOptionsModification(
            internalOptions -> internalOptions.enableSourceDebugExtensionRewriter = true)
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), main)
        .assertFailureWithErrorThatMatches(containsString("inlineExceptionStatic"))
        .inspectStackTrace(
            (stackTrace, codeInspector) -> {
              MethodSubject mainSubject = codeInspector.clazz(main).uniqueMethodWithName("main");
              InlinePosition inlineStack =
                  InlinePosition.stack(
                      InlinePosition.create(
                          "retrace.InlineFunctionKt", "inlineExceptionStatic", 3, 8),
                      InlinePosition.create(
                          "retrace.NestedInlineFunctionKt", "nestedInline", 3, 10),
                      InlinePosition.create(mainSubject.asFoundMethodSubject(), 3, 19));
              checkInlineInformation(stackTrace, codeInspector, mainSubject, inlineStack);
            });
  }

  @Test
  public void testRetraceKotlinNestedInlineFunctionOnFirstLine()
      throws ExecutionException, CompilationFailedException, IOException {
    String main = "retrace.MainNestedFirstLineKt";
    testForR8(parameters.getBackend())
        .addProgramFiles(compilationResults.apply(parameters.getRuntime()))
        .addProgramFiles(ToolHelper.getKotlinStdlibJar())
        .addKeepAttributes("SourceFile", "LineNumberTable")
        .setMode(CompilationMode.RELEASE)
        .addKeepMainRule(main)
        .addOptionsModification(
            internalOptions -> internalOptions.enableSourceDebugExtensionRewriter = true)
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), main)
        .assertFailureWithErrorThatMatches(containsString("inlineExceptionStatic"))
        .inspectStackTrace(
            (stackTrace, codeInspector) -> {
              MethodSubject mainSubject = codeInspector.clazz(main).uniqueMethodWithName("main");
              InlinePosition inlineStack =
                  InlinePosition.stack(
                      InlinePosition.create(
                          "retrace.InlineFunctionKt", "inlineExceptionStatic", 2, 8),
                      InlinePosition.create(
                          "retrace.NestedInlineFunctionKt", "nestedInlineOnFirstLine", 2, 15),
                      InlinePosition.create(mainSubject.asFoundMethodSubject(), 2, 20));
              checkInlineInformation(stackTrace, codeInspector, mainSubject, inlineStack);
            });
  }

  private void checkInlineInformation(
      StackTrace stackTrace,
      CodeInspector codeInspector,
      MethodSubject mainSubject,
      InlinePosition inlineStack) {
    assertThat(mainSubject, isPresent());
    RetraceMethodResult retraceResult =
        mainSubject
            .streamInstructions()
            .filter(InstructionSubject::isThrow)
            .collect(toSingle())
            .retracePosition(codeInspector.retrace());
    assertThat(retraceResult, isInlineFrame());
    assertThat(retraceResult, isInlineStack(inlineStack));
    assertThat(stackTrace, containsInlinePosition(inlineStack));
  }
}
