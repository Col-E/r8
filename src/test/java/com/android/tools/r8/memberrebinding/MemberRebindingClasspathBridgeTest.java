// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.memberrebinding;

import static org.junit.Assert.assertThrows;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.memberrebinding.classpathbridge.Main;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MemberRebindingClasspathBridgeTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public void runTest(ThrowableConsumer<R8FullTestBuilder> configure) throws Exception {
    testForR8(parameters.getBackend())
        .setMinApi(parameters)
        .addProgramClasses(ProgramInterface.class)
        .addProgramFiles(ToolHelper.getClassFilesForTestPackage(Main.class.getPackage()))
        .addKeepMainRule(Main.class)
        .addKeepAllClassesRule()
        .setMode(CompilationMode.RELEASE)
        .apply(configure)
        .run(parameters.getRuntime(), Main.class);
  }

  @Test
  public void runTestLibrary() throws Exception {
    runTest(
        builder ->
            builder
                .addDefaultRuntimeLibrary(parameters)
                .addLibraryClasses(ClasspathInterface.class));
  }

  @Test
  public void runTestClasspath() {
    assertThrows(
        CompilationFailedException.class,
        () -> runTest(builder -> builder.addClasspathClasses(ClasspathInterface.class)));
  }

  @Test
  public void runTestProgram() throws Exception {
    runTest(builder -> builder.addProgramClasses(ClasspathInterface.class));
  }

  /* package private */ interface ClasspathInterface {
    void m();
  }

  public interface ProgramInterface extends ClasspathInterface {}
}
