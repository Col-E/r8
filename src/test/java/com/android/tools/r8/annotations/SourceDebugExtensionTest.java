// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.annotations;

import static com.android.tools.r8.KotlinCompilerTool.KOTLINC;
import static com.android.tools.r8.ToolHelper.getFilesInTestFolderRelativeToClass;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.TestRuntime.CfRuntime;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import com.android.tools.r8.retrace.KotlinInlineFunctionRetraceTest;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.codeinspector.AnnotationSubject;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SourceDebugExtensionTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public SourceDebugExtensionTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws IOException, CompilationFailedException, ExecutionException {
    CfRuntime cfRuntime =
        parameters.isCfRuntime() ? parameters.getRuntime().asCf() : TestRuntime.getCheckedInJdk9();
    Path kotlinSources =
        kotlinc(cfRuntime, getStaticTemp(), KOTLINC, KotlinTargetVersion.JAVA_8)
            .addSourceFiles(
                getFilesInTestFolderRelativeToClass(
                    KotlinInlineFunctionRetraceTest.class, "kt", ".kt"))
            .compile();
    CodeInspector kotlinInspector = new CodeInspector(kotlinSources);
    inspectSourceDebugExtension(kotlinInspector);
    testForR8(parameters.getBackend())
        .addClasspathFiles(ToolHelper.getKotlinStdlibJar())
        .addProgramFiles(kotlinSources)
        .addKeepAttributes(ProguardKeepAttributes.SOURCE_DEBUG_EXTENSION)
        .addKeepAllClassesRule()
        .setMode(CompilationMode.RELEASE)
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(this::inspectSourceDebugExtension);
  }

  private void inspectSourceDebugExtension(CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz("retrace.InlineFunctionKt");
    assertThat(clazz, isPresent());
    AnnotationSubject sourceDebugExtensions =
        clazz.annotation("dalvik.annotation.SourceDebugExtension");
    assertThat(sourceDebugExtensions, isPresent());
  }
}
