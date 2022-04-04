// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.DexFileMergerHelper;
import com.android.tools.r8.DiagnosticsMatcher;
import com.android.tools.r8.L8;
import com.android.tools.r8.L8Command;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.StringResource;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.desugar.desugaredlibrary.jdktests.Jdk11DesugaredLibraryTestBase;
import com.android.tools.r8.errors.DuplicateTypesDiagnostic;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MergingJ$Test extends Jdk11DesugaredLibraryTestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public MergingJ$Test(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void testMergingJ$() throws Exception {
    Path mergerInputPart1 = buildSplitDesugaredLibraryPart1();
    Path mergerInputPart2 = buildSplitDesugaredLibraryPart2();
    assertThrows(
        CompilationFailedException.class,
        () ->
            testForD8()
                .addProgramFiles(mergerInputPart1, mergerInputPart2)
                .addLibraryFiles(getLibraryFile())
                .compileWithExpectedDiagnostics(
                    diagnostics -> {
                      diagnostics
                          .assertOnlyErrors()
                          .assertErrorsMatch(diagnosticType(DuplicateTypesDiagnostic.class));
                    }));
  }

  @Test
  public void testMergingJ$WithDexFileMergerHelper() throws Exception {
    Path mergerInputPart1 = buildSplitDesugaredLibraryPart1();
    Path mergerInputPart2 = buildSplitDesugaredLibraryPart2();
    Path merged = temp.newFolder().toPath().resolve("merged.jar");
    D8Command command =
        D8Command.builder()
            .addProgramFiles(mergerInputPart1, mergerInputPart2)
            .addLibraryFiles(getLibraryFile())
            .setOutput(merged, OutputMode.DexIndexed)
            .build();
    try {
      DexFileMergerHelper.run(
          command,
          true,
          ImmutableMap.<String, Integer>builder()
              .put(mergerInputPart1.toString(), 1)
              .put(mergerInputPart2.toString(), 2)
              .build());
    } catch (Exception e) {
      if (e.getCause().getMessage().contains("Merging dex file containing classes with prefix")) {
        // TODO(b/138278440): Forbid to merge j$ classes in a Google3 compliant way.
        // In Google 3 the Dex merger is used to merge the Bazel desugared core library.
        // The Dex merger has to be able to merge multiple classes with the prefix j$ for this case.
        // The following should therefore not raise:
        // "Merging dex file containing classes with prefix j$. is not allowed."
        fail();
      }
      throw e;
    }
    CodeInspector codeInspectorOutput = new CodeInspector(merged);
    CodeInspector codeInspectorSplit1 = new CodeInspector(mergerInputPart1);
    CodeInspector codeInspectorSplit2 = new CodeInspector(mergerInputPart2);
    assertNotNull(codeInspectorOutput);
    assertTrue(codeInspectorOutput.allClasses().size() > codeInspectorSplit1.allClasses().size());
    assertTrue(codeInspectorOutput.allClasses().size() > codeInspectorSplit2.allClasses().size());
  }

  private Path buildSplitDesugaredLibraryPart1() throws Exception {
    Path outputDex = temp.newFolder().toPath().resolve("merger-input-dex.zip");
    L8.run(
        L8Command.builder()
            .addLibraryFiles(getLibraryFile())
            .addProgramFiles(ToolHelper.getDesugarJDKLibs())
            .addDesugaredLibraryConfiguration(
                StringResource.fromFile(ToolHelper.getDesugarLibJsonForTesting()))
            .setMinApiLevel(AndroidApiLevel.B.getLevel())
            .setOutput(outputDex, OutputMode.DexIndexed)
            .build());
    return outputDex;
  }

  private Path buildSplitDesugaredLibraryPart2() throws Exception {
    Path outputDex = temp.newFolder().toPath().resolve("merger-input-split-dex.zip");
    L8.run(
        L8Command.builder()
            .addLibraryFiles(getLibraryFile())
            .addLibraryFiles(ToolHelper.getDesugarJDKLibs())
            .addProgramFiles(JDK_11_JAVA_BASE_EXTENSION_COMPILED_FILES)
            .addDesugaredLibraryConfiguration(
                StringResource.fromFile(ToolHelper.getDesugarLibJsonForTesting()))
            .setMinApiLevel(AndroidApiLevel.B.getLevel())
            .setOutput(outputDex, OutputMode.DexIndexed)
            .build());
    return outputDex;
  }

  @Test
  public void testJavaMergesWithEverything() throws Exception {
    // java and other prefixes can co-exist in same DEX.
    Path dexWithJavaAndNonJ$Classes =
        testForD8()
            .addProgramClassFileData(
                transformer(A.class).setClassDescriptor("Ljava/A;").transform())
            .addInnerClasses(getClass())
            .compile()
            .writeToZip();

    // Can be merged with other java classes.
    testForD8()
        .addProgramFiles(dexWithJavaAndNonJ$Classes)
        .addProgramFiles(
            testForD8()
                .addProgramClassFileData(
                    transformer(B.class).setClassDescriptor("Ljava/B;").transform())
                .compile()
                .writeToZip())
        .compile();

    // Cannot be merged with j$ classes.
    Path j$Dex =
        testForD8()
            .addProgramClassFileData(transformer(A.class).setClassDescriptor("Lj$/A;").transform())
            .compile()
            .writeToZip();
    assertThrows(
        CompilationFailedException.class,
        () ->
            testForD8()
                .addProgramFiles(dexWithJavaAndNonJ$Classes)
                .addProgramFiles(j$Dex)
                .compileWithExpectedDiagnostics(
                    diagnostics ->
                        diagnostics.assertErrorsMatch(
                            DiagnosticsMatcher.diagnosticMessage(
                                containsString(
                                    "Merging DEX file containing classes with prefix")))));

    // Cannot be merged with j$ even if they are together with java.
    Path j$JavaDex =
        testForD8()
            .addProgramClassFileData(transformer(A.class).setClassDescriptor("Lj$/A;").transform())
            .addProgramClassFileData(
                transformer(B.class).setClassDescriptor("Ljava/B;").transform())
            .compile()
            .writeToZip();
    assertThrows(
        CompilationFailedException.class,
        () ->
            testForD8()
                .addProgramFiles(dexWithJavaAndNonJ$Classes)
                .addProgramFiles(j$JavaDex)
                .compileWithExpectedDiagnostics(
                    diagnostics ->
                        diagnostics.assertErrorsMatch(
                            DiagnosticsMatcher.diagnosticMessage(
                                containsString(
                                    "Merging DEX file containing classes with prefix")))));
  }

  @Test
  public void testJ$OnlyMergesWithJava() throws Exception {
    Path j$Dex =
        testForD8()
            .addProgramClassFileData(transformer(A.class).setClassDescriptor("Lj$/A;").transform())
            .compile()
            .writeToZip();
    Path javaDex =
        testForD8()
            .addProgramClassFileData(
                transformer(A.class).setClassDescriptor("Ljava/A;").transform())
            .compile()
            .writeToZip();
    Path j$Dex2 =
        testForD8()
            .addProgramClassFileData(transformer(B.class).setClassDescriptor("Lj$/B;").transform())
            .compile()
            .writeToZip();
    Path javaDex2 =
        testForD8()
            .addProgramClassFileData(
                transformer(B.class).setClassDescriptor("Ljava/B;").transform())
            .compile()
            .writeToZip();
    Path javaJ$Dex =
        testForD8()
            .addProgramClassFileData(transformer(C.class).setClassDescriptor("Lj$/C;").transform())
            .addProgramClassFileData(
                transformer(C.class).setClassDescriptor("Ljava/C;").transform())
            .compile()
            .writeToZip();

    List<Path> j$Dexes = ImmutableList.of(j$Dex, j$Dex2, javaJ$Dex);
    List<Path> allDexes = ImmutableList.of(j$Dex, javaDex, j$Dex2, javaDex2, javaJ$Dex);
    testForD8().addProgramFiles(allDexes).compile();

    for (Path first : allDexes) {
      for (Path second : allDexes) {
        if (first != second) {
          testForD8().addProgramFiles(first, second).compile();
        }
      }
      if (j$Dexes.contains(first)) {
        assertThrows(
            CompilationFailedException.class,
            () ->
                testForD8()
                    .addInnerClasses(getClass())
                    .addProgramFiles(first)
                    .compileWithExpectedDiagnostics(
                        diagnostics ->
                            diagnostics.assertErrorsMatch(
                                DiagnosticsMatcher.diagnosticMessage(
                                    containsString(
                                        "Merging DEX file containing classes with prefix")))));
      } else {
        testForD8().addInnerClasses(getClass()).addProgramFiles(first).compile();
      }
    }
  }

  static class A {}

  static class B {}

  static class C {}
}
