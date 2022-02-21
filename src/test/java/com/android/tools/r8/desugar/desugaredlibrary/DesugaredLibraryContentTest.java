// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;

import com.android.tools.r8.D8TestBuilder;
import com.android.tools.r8.L8Command;
import com.android.tools.r8.LibraryDesugaringTestConfiguration;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.StringResource;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestDiagnosticMessagesImpl;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import java.nio.file.Path;
import java.util.ArrayList;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class DesugaredLibraryContentTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public DesugaredLibraryContentTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testInvalidLibrary() {
    Assume.assumeTrue(requiresAnyCoreLibDesugaring(parameters));
    D8TestBuilder testBuilder =
        testForD8()
            .setMinApi(parameters.getApiLevel())
            .addProgramClasses(GuineaPig.class)
            .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.L))
            .enableCoreLibraryDesugaring(
                LibraryDesugaringTestConfiguration.forApiLevel(parameters.getApiLevel()));
    try {
      testBuilder.compile();
    } catch (Throwable t) {
      // Expected since we are compiling with an invalid set-up.
    }
    TestDiagnosticMessages diagnosticMessages = testBuilder.getState().getDiagnosticsMessages();
    diagnosticMessages.assertOnlyWarnings();
    assertTrue(
        diagnosticMessages
            .getWarnings()
            .get(0)
            .getDiagnosticMessage()
            .contains(
                "Desugared library requires to be compiled with a library file of API greater or"
                    + " equal to"));
  }

  @Test
  public void testDesugaredLibraryContentD8() throws Exception {
    Assume.assumeTrue(requiresAnyCoreLibDesugaring(parameters));
    CodeInspector inspector = new CodeInspector(buildDesugaredLibrary(parameters.getApiLevel()));
    assertCorrect(inspector);
  }

  @Test
  public void testDesugaredLibraryContentR8() throws Exception {
    Assume.assumeTrue(requiresAnyCoreLibDesugaring(parameters));
    CodeInspector inspector =
        new CodeInspector(
            buildDesugaredLibrary(parameters.getApiLevel(), "-keep class * { *; }", true));
    assertCorrect(inspector);
  }

  @Test
  public void testDesugaredLibraryContentWithCoreLambdaStubsAsProgram() throws Exception {
    Assume.assumeTrue(requiresAnyCoreLibDesugaring(parameters));
    ArrayList<Path> coreLambdaStubs = new ArrayList<>();
    coreLambdaStubs.add(ToolHelper.getCoreLambdaStubs());
    CodeInspector inspector =
        new CodeInspector(
            buildDesugaredLibrary(parameters.getApiLevel(), "", false, coreLambdaStubs));
    assertCorrect(inspector);
  }

  @Test
  public void testDesugaredLibraryContentWithCoreLambdaStubsAsLibrary() throws Exception {
    Assume.assumeTrue(requiresAnyCoreLibDesugaring(parameters));
    TestDiagnosticMessagesImpl diagnosticsHandler = new TestDiagnosticMessagesImpl();
    Path desugaredLib = temp.newFolder().toPath().resolve("desugar_jdk_libs_dex.zip");
    L8Command.Builder l8Builder =
        L8Command.builder(diagnosticsHandler)
            .addLibraryFiles(getLibraryFile())
            .addProgramFiles(ToolHelper.getDesugarJDKLibs())
            .addProgramFiles(ToolHelper.DESUGAR_LIB_CONVERSIONS)
            .addLibraryFiles(ToolHelper.getCoreLambdaStubs())
            .addDesugaredLibraryConfiguration(
                StringResource.fromFile(ToolHelper.getDesugarLibJsonForTesting()))
            .setMinApiLevel(parameters.getApiLevel().getLevel())
            .setOutput(desugaredLib, OutputMode.DexIndexed);
    ToolHelper.runL8(l8Builder.build(), options -> {});
    CodeInspector codeInspector = new CodeInspector(desugaredLib);
    assertCorrect(codeInspector);
    diagnosticsHandler.assertNoMessages();
  }

  private void assertCorrect(CodeInspector inspector) {
    inspector.allClasses().forEach(clazz -> assertThat(clazz.getOriginalName(), startsWith("j$.")));
    assertThat(inspector.clazz("j$.time.Clock"), isPresent());
    // Above N the following classes are removed instead of being desugared.
    if (parameters.getApiLevel().getLevel() >= AndroidApiLevel.N.getLevel()) {
      assertFalse(inspector.clazz("j$.util.Optional").isPresent());
      assertFalse(inspector.clazz("j$.util.function.Function").isPresent());
      return;
    }
    assertThat(inspector.clazz("j$.util.Optional"), isPresent());
    assertThat(inspector.clazz("j$.util.function.Function"), isPresent());
    if (parameters.getApiLevel().isLessThan(AndroidApiLevel.K)) {
      inspector.forAllClasses(clazz -> clazz.forAllMethods(this::assertNoSupressedInvocations));
    }
  }

  private void assertNoSupressedInvocations(FoundMethodSubject method) {
    if (method.isAbstract()) {
      return;
    }
    for (InstructionSubject instruction : method.instructions()) {
      if (instruction.isInvoke() && instruction.getMethod() != null) {
        assertNotEquals(
            instruction.getMethod(), new DexItemFactory().throwableMethods.addSuppressed);
      }
    }
  }

  static class GuineaPig {

    public static void main(String[] args) {}
  }
}
