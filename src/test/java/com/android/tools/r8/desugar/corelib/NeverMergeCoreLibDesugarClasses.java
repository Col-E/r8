// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.corelib;

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
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class NeverMergeCoreLibDesugarClasses extends CoreLibDesugarTestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public NeverMergeCoreLibDesugarClasses(TestParameters parameters) {
    this.parameters = parameters;
  }

  // TODO(b/138278440): Forbid to merge j$ classes in a Google3 compliant way (remove @Ignore).
  @Test
  @Ignore
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
                    "Merging dex file containing classes with prefix 'j$.' is not allowed."));
          });
    } catch (CompilationFailedException e) {
      // Expected compilation failed.
      return;
    }
    fail("Expected test to fail with CompilationFailedException");
  }

  // TODO(b/138278440): Forbid to merge j$ classes in a Google3 compliant way (remove @Ignore).
  @Test
  @Ignore
  public void testDesugaredCoreLibrary() throws Exception {
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
                    "Merging dex file containing classes with prefix 'j$.' is not allowed."));
          });
    } catch (CompilationFailedException e) {
      // Expected compilation failed.
      return;
    }
    fail("Expected test to fail with CompilationFailedException");
  }

  @Test
  public void testTestCodeRuns() throws Exception {
    testForD8()
        .addInnerClasses(NeverMergeCoreLibDesugarClasses.class)
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
        .setMinApi(parameters.getRuntime())
        .enableCoreLibraryDesugaring()
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
