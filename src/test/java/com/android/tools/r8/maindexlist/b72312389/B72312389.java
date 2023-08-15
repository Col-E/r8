// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.maindexlist.b72312389;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import com.android.tools.r8.BaseCommand;
import com.android.tools.r8.CompatProguardCommandBuilder;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.GenerateMainDexList;
import com.android.tools.r8.GenerateMainDexListCommand;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class B72312389 extends TestBase {
  // TODO(120884788): Remove this when default is true.
  private static boolean lookupLibraryBeforeProgram = false;

  // Build a app with a class extending InstrumentationTestCase and including both the junit
  // and the Android library.
  private void buildInstrumentationTestCaseApplication(BaseCommand.Builder builder) {
    builder
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.O))
        .addProgramFiles(
            Paths.get(
                ToolHelper.TESTS_BUILD_DIR,
                "examplesAndroidO",
                "classes",
                "instrumentationtest",
                "InstrumentationTest.class"))
        .addProgramFiles(ToolHelper.getFrameworkJunitJarPath(DexVm.ART_7_0_0_HOST));
  }

  private List<String> keepInstrumentationTestCaseRules = ImmutableList.of(
      "-keep class instrumentationtest.InstrumentationTest {",
      "  *;",
      "}");

  @Test
  public void testGenerateMainDexList() throws Exception {
    assumeFalse(ToolHelper.isWindows());
    CollectingDiagnosticHandler diagnostics = new CollectingDiagnosticHandler();
    GenerateMainDexListCommand.Builder builder = GenerateMainDexListCommand.builder(diagnostics);
    buildInstrumentationTestCaseApplication(builder);
    GenerateMainDexListCommand command = builder
        .addMainDexRules(keepInstrumentationTestCaseRules, Origin.unknown())
        .build();
    List<String> mainDexList = GenerateMainDexList.run(command);
    if (lookupLibraryBeforeProgram) {
      assertFalse(mainDexList.contains("junit/framework/TestCase.class"));
    } else {
      assertTrue(mainDexList.contains("junit/framework/TestCase.class"));
    }
    diagnostics.assertEmpty();
  }

  @Test
  public void testR8ForceProguardCompatibility() throws Exception {
    assumeFalse(ToolHelper.isWindows());
    Box<String> mainDexList = new Box<>();
    // Build a app with a class extending InstrumentationTestCase and including both the junit
    // and the Android library.
    CollectingDiagnosticHandler diagnostics = new CollectingDiagnosticHandler();
    R8Command.Builder builder = new CompatProguardCommandBuilder(true, diagnostics);
    buildInstrumentationTestCaseApplication(builder);
    R8Command command =
        builder
            .setMinApiLevel(AndroidApiLevel.K.getLevel())
            // TODO(72793900): This should not be required.
            .addProguardConfiguration(ImmutableList.of("-keep class ** { *; }"), Origin.unknown())
            .addProguardConfiguration(ImmutableList.of("-dontobfuscate"), Origin.unknown())
            .addMainDexRules(keepInstrumentationTestCaseRules, Origin.unknown())
            .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
            .setMainDexListConsumer(ToolHelper.consumeString(mainDexList::set))
            .build();
    CodeInspector inspector = new CodeInspector(ToolHelper.runR8(command));
    assertTrue(inspector.clazz("instrumentationtest.InstrumentationTest").isPresent());
    assertEquals(
        !lookupLibraryBeforeProgram, mainDexList.get().contains("junit/framework/TestCase.class"));
    assertEquals(
        lookupLibraryBeforeProgram ? 0 : 1,
        diagnostics.countLibraryClassExtendsProgramClassWarnings(
            "android.test.InstrumentationTestCase", "junit.framework.TestCase"));
  }

  @Test
  public void testR8() throws Exception {
    assumeFalse(ToolHelper.isWindows());
    testForR8(Backend.DEX)
        .apply(b -> buildInstrumentationTestCaseApplication(b.getBuilder()))
        .setMinApi(AndroidApiLevel.B)
        .addMainDexRules(keepInstrumentationTestCaseRules)
        .compile()
        // Library types and method overrides are lazily enqueued, thus no longer causing failures.
        .assertNoMessages();
  }

  private static class CollectingDiagnosticHandler implements DiagnosticsHandler {
    private final List<Diagnostic> infos = new ArrayList<>();
    private final List<Diagnostic> warnings = new ArrayList<>();
    private final List<Diagnostic> errors = new ArrayList<>();

    @Override
    public void info(Diagnostic info) {
      infos.add(info);
    }

    @Override
    public void warning(Diagnostic warning) {
      warnings.add(warning);
    }

    @Override
    public void error(Diagnostic error) {
      errors.add(error);
    }

    public void assertEmpty() {
      assertEquals(0, errors.size());
      assertEquals(0, warnings.size());
      assertEquals(0, infos.size());
    }

    private boolean isLibraryClassExtendsProgramClassWarning(
        String libraryClass, String programClass, Diagnostic diagnostic) {
      return diagnostic.getDiagnosticMessage().equals(
          "Library class "+ libraryClass + " extends program class " + programClass);
    }

    public long countLibraryClassExtendsProgramClassWarnings(
        String libraryClass, String programClass) {
      return warnings.stream()
          .filter(
              diagnostics ->
                  isLibraryClassExtendsProgramClassWarning(libraryClass, programClass, diagnostics))
          .count();
    }
  }
}
