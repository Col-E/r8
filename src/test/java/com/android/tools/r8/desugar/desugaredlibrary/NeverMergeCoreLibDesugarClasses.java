// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.smali.SmaliBuilder;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableList;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class NeverMergeCoreLibDesugarClasses extends DesugaredLibraryTestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection
  data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public NeverMergeCoreLibDesugarClasses(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testMimimalDexFile() throws Exception {
    SmaliBuilder builder = new SmaliBuilder();
    builder.addClass("j$.util.function.Function");
    builder.addStaticMethod("void", "method", ImmutableList.of(), 0, "return-void");

    try {
      testForD8()
          .addInnerClasses(NeverMergeCoreLibDesugarClasses.class)
          .addProgramDexFileData(builder.compile())
          .setMinApi(parameters.getRuntime())
          .compileWithExpectedDiagnostics(diagnostics -> {
            diagnostics.assertErrorsCount(1);
            String message = diagnostics.getErrors().get(0).getDiagnosticMessage();
            assertThat(
                message,
                containsString(
                    "Merging dex file containing classes with prefix 'j$.' "
                        + "with classes with any other prefixes is not allowed."));
          });
    } catch (CompilationFailedException e) {
      // Expected compilation failed.
      return;
    }
    fail("Expected test to fail with CompilationFailedException");
  }

  @Test
  public void testDesugaredCoreLibrary() throws Exception {
    Assume.assumeTrue(parameters.getApiLevel().getLevel() < 26);
    try {
      testForD8()
          .addInnerClasses(NeverMergeCoreLibDesugarClasses.class)
          .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
          .setMinApi(parameters.getRuntime())
          .addProgramFiles(buildDesugaredLibrary(parameters.getApiLevel()))
          .compileWithExpectedDiagnostics(diagnostics -> {
            diagnostics.assertErrorsCount(1);
            String message = diagnostics.getErrors().get(0).getDiagnosticMessage();
            assertThat(
                message,
                containsString(
                    "Merging dex file containing classes with prefix 'j$.' "
                        + "with classes with any other prefixes is not allowed."));
          });
    } catch (CompilationFailedException e) {
      // Expected compilation failed.
      return;
    }
    fail("Expected test to fail with CompilationFailedException");
  }

  @Test
  public void testTestCodeRuns() throws Exception {
    // j$.util.Function is not present in recent APIs.
    Assume.assumeTrue(parameters.getApiLevel().getLevel() < 24);
    testForD8()
        .addInnerClasses(NeverMergeCoreLibDesugarClasses.class)
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
        .setMinApi(parameters.getRuntime())
        .enableCoreLibraryDesugaring(parameters.getApiLevel())
        .compile()
        .addRunClasspathFiles(buildDesugaredLibrary(parameters.getApiLevel()))
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  static class TestClass {

    public static void main(String[] args) throws Exception {
      Class.forName("j$.util.function.Function");
      System.out.println("Hello, world!");
    }
  }
}
