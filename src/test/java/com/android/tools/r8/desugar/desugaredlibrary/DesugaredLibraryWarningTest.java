// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.D8_L8DEBUG;
import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.D8_L8SHRINK;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK8;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;
import static org.hamcrest.core.StringContains.containsString;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DesugaredLibraryWarningTest extends DesugaredLibraryTestBase {

  private static final String getFunctionKeep(String prefix) {
    return "-keep class j$.util.function.Function$-CC {\n"
        + "    "
        + prefix
        + ".util.function.Function $default$compose("
        + prefix
        + ".util.function.Function,"
        + " "
        + prefix
        + ".util.function.Function);\n"
        + "    "
        + prefix
        + ".util.function.Function $default$andThen("
        + prefix
        + ".util.function.Function,"
        + " "
        + prefix
        + ".util.function.Function);\n"
        + "}\n"
        + "-keep class "
        + prefix
        + ".util.function.Function { *; }";
  }

  private static final String FUNCTION_KEEP_J$ = getFunctionKeep("j$");
  private static final String FUNCTION_KEEP_JAVA = getFunctionKeep("java");

  private final TestParameters parameters;
  private final CompilationSpecification compilationSpecification;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(),
        getJdk8Jdk11(),
        ImmutableList.of(D8_L8DEBUG, D8_L8SHRINK));
  }

  public DesugaredLibraryWarningTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.compilationSpecification = compilationSpecification;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
  }

  @Test
  public void testDesugaredLibraryContent() throws Exception {
    testForL8(parameters.getApiLevel())
        .apply(
            l8TestBuilder ->
                libraryDesugaringSpecification.configureL8TestBuilder(
                    l8TestBuilder,
                    compilationSpecification.isL8Shrink(),
                    libraryDesugaringSpecification.hasEmulatedInterfaceDesugaring(parameters)
                        ? libraryDesugaringSpecification.hasJDollarFunction(parameters)
                            ? FUNCTION_KEEP_J$
                            : FUNCTION_KEEP_JAVA
                        : ""))
        .compile()
        .inspectDiagnosticMessages(
            diagnosticsHandler -> {
              diagnosticsHandler.assertNoErrors();
              if (libraryDesugaringSpecification != JDK8) {
                diagnosticsHandler.assertAllWarningsMatch(
                    diagnosticMessage(containsString("Specification conversion")));
              } else {
                diagnosticsHandler.assertNoWarnings();
              }
              diagnosticsHandler.assertNoInfos();
            });
  }
}
