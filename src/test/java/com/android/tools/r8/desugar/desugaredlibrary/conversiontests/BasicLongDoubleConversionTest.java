// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.conversiontests;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.SPECIFICATIONS_WITH_CF2CF;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.CustomLibrarySpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import java.time.MonthDay;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

// Longs and double take two stack indexes, this had to be dealt with in
// CfAPIConverter*WrapperCodeProvider (See stackIndex vs index), this class tests that the
// synthetic Cf code is correct.
@RunWith(Parameterized.class)
public class BasicLongDoubleConversionTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;
  private final boolean forceInlineAPIConversions;

  private static final AndroidApiLevel MIN_SUPPORTED = AndroidApiLevel.O;
  private static final String EXPECTED_RESULT = StringUtils.lines("--01-16", "--01-02");

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getConversionParametersUpToExcluding(MIN_SUPPORTED),
        getJdk8Jdk11(),
        SPECIFICATIONS_WITH_CF2CF,
        BooleanUtils.values());
  }

  public BasicLongDoubleConversionTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification,
      boolean forceInlineAPIConversions) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
    this.forceInlineAPIConversions = forceInlineAPIConversions;
  }

  @Test
  public void testRewrittenAPICalls() throws Exception {
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addProgramClasses(Executor.class)
        .setCustomLibrarySpecification(
            new CustomLibrarySpecification(CustomLibClass.class, MIN_SUPPORTED))
        .addKeepMainRule(Executor.class)
        .addOptionsModification(options -> options.testing.trackDesugaredAPIConversions = true)
        .addOptionsModification(
            options -> options.testing.forceInlineAPIConversions = forceInlineAPIConversions)
        .allowDiagnosticWarningMessages()
        .compile()
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  static class Executor {

    public static void main(String[] args) {
      System.out.println(
          CustomLibClass.mix(3L, 4L, MonthDay.of(1, 2), 5.0, 6.0, MonthDay.of(10, 20)));
      System.out.println(CustomLibClass.convertThenWide(3L, 4L, MonthDay.of(1, 2), 5.0));
    }
  }

  // This class will be put at compilation time as library and on the runtime class path.
  // This class is convenient for easy testing. Each method plays the role of methods in the
  // platform APIs for which argument/return values need conversion.
  static class CustomLibClass {

    public static MonthDay mix(
        long l1, long l2, MonthDay monthDay1, double d1, double d2, MonthDay monthDay2) {
      return monthDay1.withDayOfMonth((int) (monthDay2.getDayOfMonth() + l1 - d1 + l2 - d2));
    }

    // This tests the case where only the last but one parameter requires a conversion and the last
    // parameter is wide.
    public static MonthDay convertThenWide(long l1, long l2, MonthDay monthDay1, double d1) {
      return monthDay1.withDayOfMonth((int) (l1 - d1 + l2));
    }
  }
}
