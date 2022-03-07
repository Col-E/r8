// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static org.hamcrest.core.StringContains.containsString;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.L8Command;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.StringResource;
import com.android.tools.r8.TestDiagnosticMessagesImpl;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.BooleanUtils;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class DesugaredLibraryWarningTest extends DesugaredLibraryTestBase {

  private static final String FUNCTION_KEEP =
      "-keep class j$.util.function.Function$-CC {\n"
          + "    j$.util.function.Function $default$compose(j$.util.function.Function,"
          + " j$.util.function.Function);\n"
          + "    j$.util.function.Function $default$andThen(j$.util.function.Function,"
          + " j$.util.function.Function);\n"
          + "}\n"
          + "-keep class j$.util.function.Function { *; }";

  private final TestParameters parameters;
  private final boolean shrinkDesugaredLibrary;

  @Parameterized.Parameters(name = "{1}, shrinkDesugaredLibrary: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withDexRuntimes().withAllApiLevels().build());
  }

  public DesugaredLibraryWarningTest(boolean shrinkDesugaredLibrary, TestParameters parameters) {
    this.shrinkDesugaredLibrary = shrinkDesugaredLibrary;
    this.parameters = parameters;
  }

  @Test
  public void testDesugaredLibraryContent() throws Exception {
    TestDiagnosticMessagesImpl diagnosticsHandler = new TestDiagnosticMessagesImpl();
    Path desugaredLib = temp.newFolder().toPath().resolve("desugar_jdk_libs_dex.zip");
    L8Command.Builder l8Builder =
        L8Command.builder(diagnosticsHandler)
            .addLibraryFiles(getLibraryFile())
            .addProgramFiles(ToolHelper.getDesugarJDKLibs())
            .addProgramFiles(ToolHelper.DESUGAR_LIB_CONVERSIONS)
            .setMode(shrinkDesugaredLibrary ? CompilationMode.RELEASE : CompilationMode.DEBUG)
            .addDesugaredLibraryConfiguration(
                StringResource.fromFile(ToolHelper.getDesugarLibJsonForTesting()))
            .setMinApiLevel(parameters.getApiLevel().getLevel())
            .setOutput(desugaredLib, OutputMode.DexIndexed);
    if (shrinkDesugaredLibrary) {
      l8Builder.addProguardConfiguration(
          Arrays.asList(FUNCTION_KEEP.split(System.lineSeparator())), Origin.unknown());
    }
    ToolHelper.runL8(l8Builder.build(), options -> {});
    if (isJDK11DesugaredLibrary()) {
      diagnosticsHandler.assertNoErrors();
      diagnosticsHandler.assertAllWarningsMatch(
          diagnosticMessage(containsString("Specification conversion")));
    } else {
      diagnosticsHandler.assertNoMessages();
    }
  }
}
