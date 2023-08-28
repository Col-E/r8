// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.maindexlist.b72312389;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import com.android.tools.r8.BaseCommand;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.GenerateMainDexList;
import com.android.tools.r8.GenerateMainDexListCommand;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessagesImpl;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.errors.DuplicateTypeInProgramAndLibraryDiagnostic;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.Box;
import com.google.common.collect.ImmutableList;
import java.nio.file.Paths;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
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

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return TestParameters.builder().withNoneRuntime().build();
  }

  public B72312389(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void testGenerateMainDexList() throws Exception {
    assumeFalse(ToolHelper.isWindows());
    TestDiagnosticMessagesImpl diagnostics = new TestDiagnosticMessagesImpl();
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
    diagnostics.assertNoMessages();
  }

  @Test
  public void testR8ForceProguardCompatibility() throws Exception {
    assumeFalse(ToolHelper.isWindows());
    Box<String> mainDexList = new Box<>();
    // Build a app with a class extending InstrumentationTestCase and including both the junit
    // and the Android library.
    testForR8Compat(Backend.DEX)
        .apply(b -> buildInstrumentationTestCaseApplication(b.getBuilder()))
        .setMinApi(AndroidApiLevel.K)
        // TODO(72793900): This should not be required.
        .addKeepRules(ImmutableList.of("-keep class ** { *; }"))
        .addDontObfuscate()
        .addMainDexRules(keepInstrumentationTestCaseRules)
        .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
        .setMainDexListConsumer(ToolHelper.consumeString(mainDexList::set))
        .allowDiagnosticMessages()
        .compileWithExpectedDiagnostics(
            diagnostics -> {
              diagnostics
                  .assertNoErrors()
                  .assertAllInfosMatch(
                      diagnosticType(DuplicateTypeInProgramAndLibraryDiagnostic.class));
              assertEquals(
                  lookupLibraryBeforeProgram ? 0 : 1,
                  countLibraryClassExtendsProgramClassWarnings(
                      diagnostics.getWarnings(),
                      "android.test.InstrumentationTestCase",
                      "junit.framework.TestCase"));
            })
        .inspect(
            inspector -> {
              assertTrue(inspector.clazz("instrumentationtest.InstrumentationTest").isPresent());
              assertEquals(
                  !lookupLibraryBeforeProgram,
                  mainDexList.get().contains("junit/framework/TestCase.class"));
            });
  }

  @Test
  public void testR8() throws Exception {
    assumeFalse(ToolHelper.isWindows());
    testForR8(Backend.DEX)
        .apply(b -> buildInstrumentationTestCaseApplication(b.getBuilder()))
        .setMinApi(AndroidApiLevel.B)
        .addMainDexRules(keepInstrumentationTestCaseRules)
        .compile()
        // Library types and method overrides are lazily enqueued, thus no warnings/errors.
        .assertNoMessages();
  }

  private static boolean isLibraryClassExtendsProgramClassWarning(
      String libraryClass, String programClass, Diagnostic diagnostic) {
    return diagnostic
        .getDiagnosticMessage()
        .equals("Library class " + libraryClass + " extends program class " + programClass);
  }

  public static long countLibraryClassExtendsProgramClassWarnings(
      List<Diagnostic> diagnostics, String libraryClass, String programClass) {
    return diagnostics.stream()
        .filter(
            diagnostic ->
                isLibraryClassExtendsProgramClassWarning(libraryClass, programClass, diagnostic))
        .count();
  }
}
