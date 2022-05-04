// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.conversiontests;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.D8_L8DEBUG;
import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.D8_L8SHRINK;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.core.StringContains.containsString;

import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.CustomLibrarySpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableList;
import java.time.Year;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class APIConversionFinalClassTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  private static final AndroidApiLevel MIN_SUPPORTED = AndroidApiLevel.O;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getConversionParametersUpToExcluding(MIN_SUPPORTED),
        getJdk8Jdk11(),
        ImmutableList.of(D8_L8DEBUG, D8_L8SHRINK));
  }

  public APIConversionFinalClassTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  @Test
  public void test() throws Exception {
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addProgramClasses(Executor.class)
        .setCustomLibrarySpecification(
            new CustomLibrarySpecification(CustomLibClass.class, MIN_SUPPORTED))
        .addKeepMainRule(Executor.class)
        .compile()
        .inspectDiagnosticMessages(this::assertDiagnosis)
        .run(parameters.getRuntime(), Executor.class)
        .assertFailureWithErrorThatMatches(containsString("NoSuchMethodError"));
  }

  private void assertDiagnosis(TestDiagnosticMessages d) {
    assertTrue(d.getInfos().get(0).getDiagnosticMessage().contains("libCall"));
  }

  static class Executor {

    public static void main(String[] args) {
      System.out.println(CustomLibClass.libCall(Year.now()));
    }
  }

  // This class will be put at compilation time as library and on the runtime class path.
  // This class is convenient for easy testing. Each method plays the role of methods in the
  // platform APIs for which argument/return values need conversion.
  static class CustomLibClass {

    // We use Year because Year is a final class with no custom conversion but Year has been
    // unused in the Android library so far.
    public static long libCall(Year year) {
      return 0L;
    }
  }
}
