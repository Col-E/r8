// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.conversiontests;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.DEFAULT_SPECIFICATIONS;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.CustomLibrarySpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import java.util.Arrays;
import java.util.DoubleSummaryStatistics;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.LongSummaryStatistics;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SummaryStatisticsConversionTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  private static final AndroidApiLevel MIN_SUPPORTED = AndroidApiLevel.N;
  private static final String EXPECTED_RESULT =
      StringUtils.lines(
          "Java 8+ API desugaring (library desugaring) cannot convert"
              + " from java.util.IntSummaryStatistics",
          "Java 8+ API desugaring (library desugaring) cannot convert"
              + " to java.util.LongSummaryStatistics",
          "Java 8+ API desugaring (library desugaring) cannot convert"
              + " to java.util.IntSummaryStatistics",
          "Java 8+ API desugaring (library desugaring) cannot convert"
              + " to java.util.DoubleSummaryStatistics");
  private static final String SUCCESS_EXPECTED_RESULT =
      StringUtils.lines(
          "2", "1", "42", "42", "42", "1", "42", "42", "42", "1", "42.0", "42.0", "42.0");

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getConversionParametersUpToExcluding(MIN_SUPPORTED),
        getJdk8Jdk11(),
        DEFAULT_SPECIFICATIONS);
  }

  public SummaryStatisticsConversionTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  @Test
  public void testStats() throws Exception {
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addProgramClasses(Executor.class)
        .setCustomLibrarySpecification(
            new CustomLibrarySpecification(CustomLibClass.class, MIN_SUPPORTED))
        .addKeepMainRule(Executor.class)
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  static class Executor {

    public static void main(String[] args) {
      // The realTest represents scenario applicable in Android apps, subsequent tests use
      // mocked CustomLib to ensure all cases are correct.
      realTest();
      longTest();
      intTest();
      doubleTest();
    }

    private static void realTest() {
      try {
        System.out.println("foo".subSequence(0, 2).codePoints().summaryStatistics().getCount());
      } catch (Error e) {
        System.out.println(e.getMessage());
      }
    }

    public static void longTest() {
      long[] longs = new long[1];
      longs[0] = 42L;
      try {
        LongSummaryStatistics mix =
            CustomLibClass.mix(
                Arrays.stream(longs).summaryStatistics(), new LongSummaryStatistics());
        System.out.println(mix.getCount());
        System.out.println(mix.getMin());
        System.out.println(mix.getMax());
        System.out.println(mix.getSum());
      } catch (Error e) {
        System.out.println(e.getMessage());
      }
    }

    public static void intTest() {
      int[] ints = new int[1];
      ints[0] = 42;
      try {
        IntSummaryStatistics mix =
            CustomLibClass.mix(Arrays.stream(ints).summaryStatistics(), new IntSummaryStatistics());
        System.out.println(mix.getCount());
        System.out.println(mix.getMin());
        System.out.println(mix.getMax());
        System.out.println(mix.getSum());
      } catch (Error e) {
        System.out.println(e.getMessage());
      }
    }

    public static void doubleTest() {
      double[] doubles = new double[1];
      doubles[0] = 42L;
      try {
        DoubleSummaryStatistics mix =
            CustomLibClass.mix(
                Arrays.stream(doubles).summaryStatistics(), new DoubleSummaryStatistics());
        System.out.println(mix.getCount());
        System.out.println(mix.getMin());
        System.out.println(mix.getMax());
        System.out.println(mix.getSum());
      } catch (Error e) {
        System.out.println(e.getMessage());
      }
    }
  }

  // This class will be put at compilation time as library and on the runtime class path.
  // This class is convenient for easy testing. Each method plays the role of methods in the
  // platform APIs for which argument/return values need conversion.
  static class CustomLibClass {

    public static LongSummaryStatistics mix(
        LongSummaryStatistics stats1, LongSummaryStatistics stats2) {
      return stats1;
    }

    public static IntSummaryStatistics mix(
        IntSummaryStatistics stats1, IntSummaryStatistics stats2) {
      return stats1;
    }

    public static DoubleSummaryStatistics mix(
        DoubleSummaryStatistics stats1, DoubleSummaryStatistics stats2) {
      return stats1;
    }
  }
}
