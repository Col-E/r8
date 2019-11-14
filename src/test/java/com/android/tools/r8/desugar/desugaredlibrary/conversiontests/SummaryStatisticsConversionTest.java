// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.conversiontests;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.DoubleSummaryStatistics;
import java.util.IntSummaryStatistics;
import java.util.LongSummaryStatistics;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SummaryStatisticsConversionTest extends APIConversionTestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    // Below 7 XXXSummaryStatistics are not present and conversions are pointless.
    return getTestParameters()
        .withDexRuntimesStartingFromIncluding(Version.V7_0_0)
        .withAllApiLevels()
        .build();
  }

  public SummaryStatisticsConversionTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testStats() throws Exception {
    Path customLib =
        testForD8()
            .setMinApi(parameters.getApiLevel())
            .addProgramClasses(CustomLibClass.class)
            .compile()
            .writeToZip();
    testForD8()
        .setMinApi(parameters.getApiLevel())
        .addProgramClasses(Executor.class)
        .addLibraryClasses(CustomLibClass.class)
        .enableCoreLibraryDesugaring(parameters.getApiLevel())
        .compile()
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibraryWithConversionExtension, parameters.getApiLevel())
        .addRunClasspathFiles(customLib)
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutput(
            StringUtils.lines(
                "2", "1", "42", "42", "42", "1", "42", "42", "42", "1", "42.0", "42.0", "42.0"));
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
      System.out.println("foo".subSequence(0, 2).codePoints().summaryStatistics().getCount());
    }

    public static void longTest() {
      long[] longs = new long[1];
      longs[0] = 42L;
      LongSummaryStatistics mix =
          CustomLibClass.mix(Arrays.stream(longs).summaryStatistics(), new LongSummaryStatistics());
      System.out.println(mix.getCount());
      System.out.println(mix.getMin());
      System.out.println(mix.getMax());
      System.out.println(mix.getSum());
    }

    public static void intTest() {
      int[] ints = new int[1];
      ints[0] = 42;
      IntSummaryStatistics mix =
          CustomLibClass.mix(Arrays.stream(ints).summaryStatistics(), new IntSummaryStatistics());
      System.out.println(mix.getCount());
      System.out.println(mix.getMin());
      System.out.println(mix.getMax());
      System.out.println(mix.getSum());
    }

    public static void doubleTest() {
      double[] doubles = new double[1];
      doubles[0] = 42L;
      DoubleSummaryStatistics mix =
          CustomLibClass.mix(
              Arrays.stream(doubles).summaryStatistics(), new DoubleSummaryStatistics());
      System.out.println(mix.getCount());
      System.out.println(mix.getMin());
      System.out.println(mix.getMax());
      System.out.println(mix.getSum());
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
