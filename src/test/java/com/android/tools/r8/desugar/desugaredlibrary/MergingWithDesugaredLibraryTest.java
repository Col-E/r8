// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.desugar.desugaredlibrary.jdktests.Jdk11DesugaredLibraryTestBase;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.nio.file.Path;
import java.util.ArrayList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MergingWithDesugaredLibraryTest extends Jdk11DesugaredLibraryTestBase {

  private static final String JAVA_RESULT = "java.util.stream.ReferencePipeline$Head";
  private static final String J$_RESULT = "j$.util.stream.ReferencePipeline$Head";

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public MergingWithDesugaredLibraryTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testMergeDesugaredAndNonDesugared() throws Exception {
    D8TestCompileResult compileResult =
        testForD8()
            .addProgramFiles(buildPart1DesugaredLibrary(), buildPart2NoDesugaredLibrary())
            .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
            .setMinApi(parameters.getApiLevel())
            .enableCoreLibraryDesugaring(parameters.getApiLevel())
            .compile()
            .addDesugaredCoreLibraryRunClassPath(
                this::buildDesugaredLibrary, parameters.getApiLevel());
    // TODO(b/154106502): This should raise a proper warning. The dex files are incompatible,
    //  so the behavior is undefined regarding desugared types.
    if (parameters.getApiLevel().getLevel() < AndroidApiLevel.N.getLevel()) {
      compileResult
          .run(parameters.getRuntime(), Part1.class)
          .assertSuccessWithOutputLines(J$_RESULT);
    } else {
      compileResult
          .run(parameters.getRuntime(), Part1.class)
          .assertSuccessWithOutputLines(JAVA_RESULT);
    }
    if (parameters.getRuntime().asDex().getMinApiLevel().getLevel()
        < AndroidApiLevel.N.getLevel()) {
      compileResult
          .run(parameters.getRuntime(), Part2.class)
          .assertFailureWithErrorThatMatches(containsString("java.lang.NoSuchMethodError"));
    } else {

      compileResult
          .run(parameters.getRuntime(), Part2.class)
          .assertSuccessWithOutputLines(JAVA_RESULT);
    }
  }

  @Test
  public void testMergeDesugaredAndClassFile() throws Exception {
    D8TestCompileResult compileResult =
        testForD8()
            .addProgramFiles(buildPart1DesugaredLibrary())
            .addProgramClasses(Part2.class)
            .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
            .setMinApi(parameters.getApiLevel())
            .enableCoreLibraryDesugaring(parameters.getApiLevel())
            .compile()
            .inspectDiagnosticMessages(this::assertWarningPresent)
            .addDesugaredCoreLibraryRunClassPath(
                this::buildDesugaredLibrary, parameters.getApiLevel());
    if (parameters.getApiLevel().getLevel() < AndroidApiLevel.N.getLevel()) {
      compileResult
          .run(parameters.getRuntime(), Part1.class)
          .assertSuccessWithOutputLines(J$_RESULT);
      compileResult
          .run(parameters.getRuntime(), Part2.class)
          .assertSuccessWithOutputLines(J$_RESULT);
    } else {
      compileResult
          .run(parameters.getRuntime(), Part1.class)
          .assertSuccessWithOutputLines(JAVA_RESULT);
      compileResult
          .run(parameters.getRuntime(), Part2.class)
          .assertSuccessWithOutputLines(JAVA_RESULT);
    }
  }

  private void assertWarningPresent(TestDiagnosticMessages testDiagnosticMessages) {
    if (parameters.getApiLevel().getLevel() > AndroidApiLevel.N.getLevel()) {
      return;
    }
    assertTrue(
        testDiagnosticMessages.getWarnings().stream()
            .anyMatch(
                warn -> warn.getDiagnosticMessage().startsWith("The compilation is slowed down")));
  }

  private Path buildPart1DesugaredLibrary() throws Exception {
    return testForD8()
        .addProgramClasses(Part1.class)
        .setMinApi(parameters.getApiLevel())
        .enableCoreLibraryDesugaring(parameters.getApiLevel())
        .compile()
        .writeToZip();
  }

  private Path buildPart2NoDesugaredLibrary() throws Exception {
    return testForD8()
        .addProgramClasses(Part2.class)
        .setMinApi(parameters.getApiLevel())
        .compile()
        .writeToZip();
  }

  @SuppressWarnings("RedundantOperationOnEmptyContainer")
  static class Part1 {
    public static void main(String[] args) {
      System.out.println(new ArrayList<>().stream().getClass().getName());
    }
  }

  @SuppressWarnings("RedundantOperationOnEmptyContainer")
  static class Part2 {
    public static void main(String[] args) {
      System.out.println(new ArrayList<>().stream().getClass().getName());
    }
  }
}
