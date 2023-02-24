// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.naming.testclasses.A;
import com.android.tools.r8.naming.testclasses.B;
import com.android.tools.r8.utils.FileUtils;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/** This test is designed to test the workaround for b/149946708. */
@RunWith(Parameterized.class)
public class ClassNameMinifierOriginalClassNameTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ClassNameMinifierOriginalClassNameTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private static Function<TestParameters, R8TestCompileResult> compilationResults =
      memoizeFunction(ClassNameMinifierOriginalClassNameTest::compile);

  private static R8TestCompileResult compile(TestParameters parameters)
      throws CompilationFailedException, IOException {
    // Adding the obfuscation dictionary just ensures that we assign a name to B that will collide
    // independent of minification scheme.
    Path dictionary = getStaticTemp().newFolder().toPath().resolve("dictionary.txt");
    FileUtils.writeTextFile(dictionary, "A");
    return testForR8(getStaticTemp(), parameters.getBackend())
        .addProgramClasses(A.class, B.class)
        .addKeepClassAndMembersRulesWithAllowObfuscation(B.class)
        .addKeepRules("-classobfuscationdictionary " + dictionary.toString(), "-keeppackagenames")
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              assertEquals(1, inspector.allClasses().size());
              assertThat(inspector.clazz(B.class), isPresentAndRenamed());
              assertEquals(A.class.getTypeName(), inspector.clazz(B.class).getFinalName());
            });
  }

  @Test
  public void testR8() throws ExecutionException, CompilationFailedException, IOException {
    R8TestCompileResult libraryCompileResult = compilationResults.apply(parameters);
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class)
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .addDontObfuscate()
        .addClasspathClasses(A.class, B.class)
        .addApplyMapping(libraryCompileResult.getProguardMap())
        .compile()
        .addRunClasspathFiles(libraryCompileResult.writeToZip())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("B.foo");
  }

  @Test
  public void testR8WithReferenceToNotMapped() {
    assumeTrue(parameters.isDexRuntime());
    R8TestCompileResult libraryCompileResult = compilationResults.apply(parameters);
    assertThrows(
        CompilationFailedException.class,
        () ->
            testForR8(parameters.getBackend())
                .addProgramClasses(MainWithReferenceToNotMapped.class)
                .setMinApi(parameters)
                .addKeepMainRule(MainWithReferenceToNotMapped.class)
                .addDontObfuscate()
                .addClasspathClasses(A.class, B.class)
                .addApplyMapping(libraryCompileResult.getProguardMap())
                .allowDiagnosticWarningMessages()
                .compileWithExpectedDiagnostics(
                    diagnostics ->
                        diagnostics.assertAllWarningsMatch(
                            diagnosticMessage(
                                containsString(
                                    "'"
                                        + B.class.getTypeName()
                                        + "' cannot be mapped to '"
                                        + A.class.getTypeName()
                                        + "' because it is in conflict")))));
  }

  public static class Main {

    public static void main(String[] args) {
      new B().foo();
    }
  }

  public static class MainWithReferenceToNotMapped {

    public static void main(String[] args) {
      System.out.println(new A());
      new B().foo();
    }
  }
}
