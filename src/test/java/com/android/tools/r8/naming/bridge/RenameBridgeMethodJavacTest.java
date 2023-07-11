// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.bridge;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** Regression test for b/290711987. */
@RunWith(Parameterized.class)
public class RenameBridgeMethodJavacTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withCfRuntimes().build();
  }

  @Test
  public void testJavac() throws Exception {
    Path library =
        javac(parameters.asCfRuntime())
            .addSourceFiles(
                ToolHelper.getSourceFileForTestClass(Creator.class),
                ToolHelper.getSourceFileForTestClass(Result.class),
                ToolHelper.getSourceFileForTestClass(ResultImpl.class),
                ToolHelper.getSourceFileForTestClass(Tester.class),
                ToolHelper.getSourceFileForTestClass(TesterImpl.class))
            .compile();
    ProcessResult processResult = compileAgainstLibrary(library);
    assertEquals(0, processResult.exitCode);
    testForJvm(parameters)
        .addRunClasspathFiles(library)
        .addRunClasspathFiles(library.getParent())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello World!");
  }

  @Test
  public void testR8() throws Exception {
    R8TestCompileResult libraryCompileResult =
        testForR8(parameters.getBackend())
            .addProgramClasses(
                Creator.class, Result.class, ResultImpl.class, Tester.class, TesterImpl.class)
            .setMinApi(parameters)
            .addKeepClassAndMembersRules(
                Creator.class, Result.class, ResultImpl.class, Tester.class)
            .addKeepClassAndMembersRulesWithAllowObfuscation(TesterImpl.class)
            .compile();
    Path libraryZip = libraryCompileResult.writeToZip();
    ProcessResult processResult = compileAgainstLibrary(libraryZip);
    // TODO(b/290711987): The minifier renames the target of a bridge preventing additional
    //  compilations to succeed.
    assertEquals(1, processResult.exitCode);
  }

  private ProcessResult compileAgainstLibrary(Path classPath) throws Exception {
    return javac(parameters.asCfRuntime())
        .addSourceFiles(ToolHelper.getSourceFileForTestClass(Main.class))
        .addClasspathFiles(classPath)
        .setOutputPath(classPath.getParent())
        .compileRaw();
  }
}
