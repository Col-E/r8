// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import static com.android.tools.r8.KotlinCompilerTool.KOTLINC;
import static com.android.tools.r8.ToolHelper.getFilesInTestFolderRelativeToClass;
import static org.hamcrest.core.StringContains.containsString;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
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

  @Test
  public void testRuntime() throws ExecutionException, CompilationFailedException, IOException {
    testForRuntime(parameters)
        .addProgramFiles(
            kotlinc(
                    parameters.isCfRuntime()
                        ? parameters.getRuntime().asCf()
                        : TestRuntime.getCheckedInJdk9(),
                    KOTLINC,
                    KotlinTargetVersion.JAVA_8)
                .addSourceFiles(
                    getFilesInTestFolderRelativeToClass(
                        KotlinInlineFunctionRetraceTest.class, "kt", ".kt"))
                .compile())
        .addRunClasspathFiles(buildOnDexRuntime(parameters, ToolHelper.getKotlinStdlibJar()))
        .run(parameters.getRuntime(), "retrace.MainKt")
        .assertFailureWithErrorThatMatches(containsString("inlineExceptionStatic"))
        .assertFailureWithErrorThatMatches(containsString("at retrace.MainKt.main(Main.kt:15)"));
  }

  @Test
  public void testRetraceKotlinInlineStaticFunction()
      throws ExecutionException, CompilationFailedException, IOException {
    String main = "retrace.MainKt";
    R8TestRunResult result =
        testForR8(parameters.getBackend())
            .addProgramFiles(
                kotlinc(
                        parameters.isCfRuntime()
                            ? parameters.getRuntime().asCf()
                            : TestRuntime.getCheckedInJdk9(),
                        KOTLINC,
                        KotlinTargetVersion.JAVA_8)
                    .addSourceFiles(
                        getFilesInTestFolderRelativeToClass(
                            KotlinInlineFunctionRetraceTest.class, "kt", ".kt"))
                    .compile())
            .addProgramFiles(ToolHelper.getKotlinStdlibJar())
            .addKeepAttributes("SourceFile", "LineNumberTable")
            .setMode(CompilationMode.RELEASE)
            .addKeepMainRule(main)
            .setMinApi(parameters.getApiLevel())
            .run(parameters.getRuntime(), main)
            .assertFailureWithErrorThatMatches(containsString("inlineExceptionStatic"))
            .assertFailureWithErrorThatMatches(containsString("at retrace.MainKt.main(Main.kt:2)"));
    List<String> retrace = result.retrace();
    // TODO(b/141817471): Change the tracing information when solved.
    // assertThat(retrace.get(1), containsString("at retrace.MainKt.main(Main.kt:15)"));
  }

  @Test
  public void testRetraceKotlinInlineInstanceFunction()
      throws ExecutionException, CompilationFailedException, IOException {
    String main = "retrace.MainInstanceKt";
    R8TestRunResult result =
        testForR8(parameters.getBackend())
            .addProgramFiles(
                kotlinc(
                        parameters.isCfRuntime()
                            ? parameters.getRuntime().asCf()
                            : TestRuntime.getCheckedInJdk9(),
                        KOTLINC,
                        KotlinTargetVersion.JAVA_8)
                    .addSourceFiles(
                        getFilesInTestFolderRelativeToClass(
                            KotlinInlineFunctionRetraceTest.class, "kt", ".kt"))
                    .compile())
            .addProgramFiles(ToolHelper.getKotlinStdlibJar())
            .addKeepAttributes("SourceFile", "LineNumberTable")
            .setMode(CompilationMode.RELEASE)
            .addKeepMainRule(main)
            .setMinApi(parameters.getApiLevel())
            .run(parameters.getRuntime(), main)
            .assertFailureWithErrorThatMatches(containsString("inlineExceptionInstance"))
            .assertFailureWithErrorThatMatches(
                containsString("at retrace.MainInstanceKt.main(MainInstance.kt:2)"));
    List<String> retrace = result.retrace();
    // TODO(b/141817471): Change the tracing information when solved.
    // assertThat(retrace.get(1), containsString("at
    // retrace.MainInstanceKt.main(MainInstance.kt:13)"));
  }

  @Test
  public void testRetraceKotlinNestedInlineFunction()
      throws ExecutionException, CompilationFailedException, IOException {
    // TODO(b/141817471): Change the tracing information when solved.
    int lineNumber = parameters.isCfRuntime() ? 4 : 3;
    String main = "retrace.MainNestedKt";
    R8TestRunResult result =
        testForR8(parameters.getBackend())
            .addProgramFiles(
                kotlinc(
                        parameters.isCfRuntime()
                            ? parameters.getRuntime().asCf()
                            : TestRuntime.getCheckedInJdk9(),
                        KOTLINC,
                        KotlinTargetVersion.JAVA_8)
                    .addSourceFiles(
                        getFilesInTestFolderRelativeToClass(
                            KotlinInlineFunctionRetraceTest.class, "kt", ".kt"))
                    .compile())
            .addProgramFiles(ToolHelper.getKotlinStdlibJar())
            .addKeepAttributes("SourceFile", "LineNumberTable")
            .setMode(CompilationMode.RELEASE)
            .addKeepMainRule(main)
            .setMinApi(parameters.getApiLevel())
            .run(parameters.getRuntime(), main)
            .assertFailureWithErrorThatMatches(containsString("inlineExceptionStatic"))
            .assertFailureWithErrorThatMatches(
                containsString("at retrace.MainNestedKt.main(MainNested.kt:" + lineNumber + ")"));
    List<String> retrace = result.retrace();
    // TODO(b/141817471): Change the tracing information when solved.
    // assertThat(retrace.get(1), containsString("at retrace.MainNestedKt.main(MainNested.kt:19)"));
  }
}
