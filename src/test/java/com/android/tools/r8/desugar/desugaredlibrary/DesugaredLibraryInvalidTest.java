// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.ToolHelper.DESUGARED_JDK_8_LIB_JAR;
import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.D8_L8DEBUG;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.CustomConversionVersion.LATEST;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.CustomConversionVersion.LEGACY;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.DesugaredLibraryTestBuilder;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.List;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DesugaredLibraryInvalidTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final CompilationSpecification compilationSpecification;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    LibraryDesugaringSpecification jdk8InvalidLib =
        new LibraryDesugaringSpecification(
            "JDK8_INVALID_LIB",
            DESUGARED_JDK_8_LIB_JAR,
            "desugar_jdk_libs.json",
            AndroidApiLevel.L,
            LibraryDesugaringSpecification.JDK8_DESCRIPTOR,
            LEGACY);
    LibraryDesugaringSpecification jdk11InvalidLib =
        new LibraryDesugaringSpecification(
            "JDK11_INVALID_LIB",
            LibraryDesugaringSpecification.getTempLibraryJDK11Undesugar(),
            "jdk11/desugar_jdk_libs.json",
            AndroidApiLevel.L,
            LibraryDesugaringSpecification.JDK11_DESCRIPTOR,
            LATEST);
    return buildParameters(
        getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build(),
        ImmutableList.of(jdk8InvalidLib, jdk11InvalidLib),
        ImmutableList.of(D8_L8DEBUG));
  }

  public DesugaredLibraryInvalidTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.compilationSpecification = compilationSpecification;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
  }

  @Test
  public void testInvalidLibrary() throws IOException {
    Assume.assumeTrue(libraryDesugaringSpecification.hasAnyDesugaring(parameters));
    DesugaredLibraryTestBuilder<?> testBuilder =
        testForDesugaredLibrary(
                parameters, libraryDesugaringSpecification, compilationSpecification)
            .addProgramClasses(GuineaPig.class);
    try {
      testBuilder.compile();
    } catch (Throwable t) {
      // Expected since we are compiling with an invalid set-up.
    }
    testBuilder.applyOnBuilder(
        b -> {
          TestDiagnosticMessages diagnosticsMessages = b.getState().getDiagnosticsMessages();
          assertFalse(diagnosticsMessages.getWarnings().isEmpty());
          assertTrue(
              diagnosticsMessages
                  .getWarnings()
                  .get(0)
                  .getDiagnosticMessage()
                  .contains(
                      "Desugared library requires to be compiled with a library file of API greater"
                          + " or equal to"));
        });
  }

  static class GuineaPig {
    public static void main(String[] args) {}
  }
}
