// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.L8Command;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.StringResource;
import com.android.tools.r8.TestDiagnosticMessagesImpl;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
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
  public void testDesugaredLibraryContent() throws Exception {
    Assume.assumeTrue(requiresAnyCoreLibDesugaring(parameters));
    CodeInspector inspector = new CodeInspector(buildDesugaredLibrary(parameters.getApiLevel()));
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
            .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
            .addProgramFiles(ToolHelper.getDesugarJDKLibs())
            .addProgramFiles(ToolHelper.DESUGAR_LIB_CONVERSIONS)
            .addLibraryFiles(ToolHelper.getCoreLambdaStubs())
            .addDesugaredLibraryConfiguration(
                StringResource.fromFile(ToolHelper.DESUGAR_LIB_JSON_FOR_TESTING))
            .setMinApiLevel(parameters.getApiLevel().getLevel())
            .setOutput(desugaredLib, OutputMode.DexIndexed);
    ToolHelper.runL8(l8Builder.build(), options -> {});
    CodeInspector codeInspector = new CodeInspector(desugaredLib);
    assertCorrect(codeInspector);
    assertNoWarningsErrors(diagnosticsHandler);
  }

  private void assertNoWarningsErrors(TestDiagnosticMessagesImpl diagnosticsHandler) {
    assertTrue(diagnosticsHandler.getWarnings().isEmpty());
    assertTrue(diagnosticsHandler.getErrors().isEmpty());
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
  }

}
