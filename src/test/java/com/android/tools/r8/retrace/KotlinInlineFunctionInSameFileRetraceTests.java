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
public class KotlinInlineFunctionInSameFileRetraceTests extends TestBase {

  private static final String FILENAME_INLINE = "InlineFunctionsInSameFile.kt";
  private static final String MAIN = "retrace.InlineFunctionsInSameFileKt";

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public KotlinInlineFunctionInSameFileRetraceTests(TestParameters parameters) {
    this.parameters = parameters;
  }

  private static Function<TestRuntime, Path> compilationResults =
      memoizeFunction(KotlinInlineFunctionInSameFileRetraceTests::compileKotlinCode);

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
        .run(parameters.getRuntime(), MAIN)
        .assertFailureWithErrorThatMatches(containsString("foo"))
        .assertFailureWithErrorThatMatches(
            containsString(
                "at retrace.InlineFunctionsInSameFileKt.main(InlineFunctionsInSameFile.kt:43"));
  }

  @Test
  public void testRetraceKotlinInlineStaticFunction()
      throws ExecutionException, CompilationFailedException, IOException {
    Path kotlinSources = compilationResults.apply(parameters.getRuntime());
    CodeInspector kotlinInspector = new CodeInspector(kotlinSources);
    testForR8(parameters.getBackend())
        .addProgramFiles(compilationResults.apply(parameters.getRuntime()))
        .addProgramFiles(ToolHelper.getKotlinStdlibJar())
        .addKeepAttributes("SourceFile", "LineNumberTable")
        .setMode(CompilationMode.RELEASE)
        .addKeepMainRule(MAIN)
        .allowDiagnosticWarningMessages()
        .noMinification()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .assertAllWarningMessagesMatch(equalTo("Resource 'META-INF/MANIFEST.MF' already exists."))
        .run(parameters.getRuntime(), MAIN)
        .assertFailureWithErrorThatMatches(containsString("main"))
        .inspectStackTrace(
            (stackTrace, codeInspector) -> {
              MethodSubject mainSubject = codeInspector.clazz(MAIN).uniqueMethodWithName("main");
              LinePosition inlineStack =
                  LinePosition.stack(
                      LinePosition.create(
                          kotlinInspector
                              .clazz("retrace.InlineFunctionsInSameFileKt")
                              .uniqueMethodWithName("foo")
                              .asFoundMethodSubject(),
                          1,
                          8,
                          FILENAME_INLINE),
                      LinePosition.create(
                          mainSubject.asFoundMethodSubject(), 1, 43, FILENAME_INLINE));
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
