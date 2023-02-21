// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.stringplus;

import static com.android.tools.r8.ToolHelper.getFilesInTestFolderRelativeToClass;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.KotlinCompilerTool;
import com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion;
import com.android.tools.r8.KotlinTestBase;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeMatchers;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
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
public class StringPlusTest extends KotlinTestBase {

  private static final String MAIN = "com.android.tools.r8.kotlin.sealed.kt.StringPlusKt";
  private static final String[] EXPECTED =
      new String[] {
        "Hello World!",
        "Hello World!",
        "StringConcat(Hello World!)",
        "StringBuilder[Hello World!]",
        "abc"
      };

  private final TestParameters parameters;

  @Parameters(name = "{0}, {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        getKotlinTestParameters().withAllCompilersAndTargetVersions().build());
  }

  public StringPlusTest(TestParameters parameters, KotlinTestParameters kotlinParameters) {
    super(kotlinParameters);
    this.parameters = parameters;
  }

  private static final KotlinCompileMemoizer compilationResults =
      getCompileMemoizer(getKotlinSources()).configure(KotlinCompilerTool::includeRuntime);

  private static Collection<Path> getKotlinSources() {
    try {
      return getFilesInTestFolderRelativeToClass(StringPlusTest.class, "kt", ".kt");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void testRuntime() throws ExecutionException, CompilationFailedException, IOException {
    testForRuntime(parameters)
        .addProgramFiles(compilationResults.getForConfiguration(kotlinc, targetVersion))
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8() throws ExecutionException, CompilationFailedException, IOException {
    testForR8(parameters.getBackend())
        .addProgramFiles(compilationResults.getForConfiguration(kotlinc, targetVersion))
        .addProgramFiles(kotlinc.getKotlinAnnotationJar())
        .setMinApi(parameters)
        .allowAccessModification()
        .allowDiagnosticWarningMessages()
        .addKeepMainRule(MAIN)
        .addKeepRules("-keep class " + MAIN + "{ void keepFor*(...); }")
        .addDontObfuscate()
        .compile()
        .inspect(
            inspector -> {
              if (parameters.isCfRuntime()) {
                return;
              }
              ClassSubject clazz = inspector.clazz(MAIN);
              assertThat(clazz, isPresent());
              MethodSubject methodSubject = clazz.mainMethod();
              assertThat(methodSubject, isPresent());
              if (kotlinParameters.isNewerThanOrEqualTo(KotlinCompilerVersion.KOTLINC_1_5_0)
                  && kotlinParameters.isOlderThan(KotlinCompilerVersion.KOTLINC_1_7_0)) {
                // TODO(b/190489514): We should be able to optimize constant stringPlus calls.
                assertThat(methodSubject, CodeMatchers.invokesMethodWithName("stringPlus"));
              }
              // We cannot remove the <init> -> <append> call since that changes the capacity
              // and the string builder is escaping into System.out.
              assertEquals(
                  1,
                  methodSubject
                      .streamInstructions()
                      .filter(
                          instructionSubject ->
                              CodeMatchers.isInvokeWithTarget(
                                      typeName(StringBuilder.class), "append")
                                  .test(instructionSubject))
                      .count());
            })
        .assertAllWarningMessagesMatch(equalTo("Resource 'META-INF/MANIFEST.MF' already exists."))
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutputLines(EXPECTED);
  }
}
