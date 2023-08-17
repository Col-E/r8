// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.optimize.defaultarguments;

import static com.android.tools.r8.ToolHelper.getFilesInTestFolderRelativeToClass;
import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethod;
import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsentIf;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.KotlinTestBase;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KotlinDefaultArgumentsTest extends KotlinTestBase {

  private static final String MAIN =
      "com.android.tools.r8.kotlin.optimize.defaultarguments.kt.DefaultArgumentsKt";
  private static final List<String> EXPECTED_OUTPUT =
      ImmutableList.of("0", "0", "1", "0", "1", "2", "0", "1", "2", "3");

  private final TestParameters parameters;

  @Parameters(name = "{0}, {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        getKotlinTestParameters().withAllCompilersAndTargetVersions().build());
  }

  public KotlinDefaultArgumentsTest(
      TestParameters parameters, KotlinTestParameters kotlinParameters) {
    super(kotlinParameters);
    this.parameters = parameters;
  }

  private static final KotlinCompileMemoizer compilationResults =
      getCompileMemoizer(getKotlinSources());

  private static Collection<Path> getKotlinSources() {
    try {
      return getFilesInTestFolderRelativeToClass(KotlinDefaultArgumentsTest.class, "kt", ".kt");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void testJvm() throws ExecutionException, CompilationFailedException, IOException {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramFiles(compilationResults.getForConfiguration(kotlinc, targetVersion))
        .addRunClasspathFiles(buildOnDexRuntime(parameters, kotlinc.getKotlinStdlibJar()))
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutputLines(EXPECTED_OUTPUT);
  }

  @Test
  public void testD8() throws ExecutionException, CompilationFailedException, IOException {
    parameters.assumeDexRuntime();
    testForD8(parameters.getBackend())
        .addProgramFiles(compilationResults.getForConfiguration(kotlinc, targetVersion))
        .addRunClasspathFiles(buildOnDexRuntime(parameters, kotlinc.getKotlinStdlibJar()))
        .setMinApi(parameters)
        .compile()
        .inspect(inspector -> inspect(inspector, false))
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutputLines(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws ExecutionException, CompilationFailedException, IOException {
    testForR8(parameters.getBackend())
        .addProgramFiles(compilationResults.getForConfiguration(kotlinc, targetVersion))
        .addProgramFiles(kotlinc.getKotlinStdlibJar())
        .addProgramFiles(kotlinc.getKotlinAnnotationJar())
        .addKeepMainRule(MAIN)
        .allowAccessModification()
        .allowDiagnosticWarningMessages()
        .setMinApi(parameters)
        .compile()
        .inspect(inspector -> inspect(inspector, true))
        .assertAllWarningMessagesMatch(equalTo("Resource 'META-INF/MANIFEST.MF' already exists."))
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutputLines(EXPECTED_OUTPUT);
  }

  private void inspect(CodeInspector inspector, boolean isR8) {
    ClassSubject mainClassSubject = inspector.clazz(MAIN);
    assertThat(mainClassSubject, isPresent());

    MethodSubject mainMethodSubject = mainClassSubject.mainMethod();
    assertThat(mainMethodSubject, isPresent());

    MethodSubject readDefaultMethodSubject =
        mainClassSubject.uniqueMethodWithOriginalName("read$default");
    assertThat(readDefaultMethodSubject, isPresent());

    MethodSubject readMethodSubject = mainClassSubject.uniqueMethodWithOriginalName("read");
    assertThat(readMethodSubject, isAbsentIf(isR8));

    if (isR8) {
      assertThat(mainMethodSubject, invokesMethod(readDefaultMethodSubject));
    } else {
      assertThat(mainMethodSubject, invokesMethod(readDefaultMethodSubject));
      assertThat(readDefaultMethodSubject, invokesMethod(readMethodSubject));
    }

    // The off parameter should be removed in R8 since the test always uses the default value.
    // Additionally, an unused parameter used to ensure a unique signature is removed.
    assertEquals(isR8 ? 3 : 5, readDefaultMethodSubject.getParameters().size());

    // The default value for the len parameter should be removed in R8 since it is never used.
    assertEquals(
        isR8,
        readDefaultMethodSubject.streamInstructions().noneMatch(InstructionSubject::isArrayLength));
  }
}
