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
import com.android.tools.r8.utils.StringUtils;
import java.time.MonthDay;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class FreezePeriodTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  private static final AndroidApiLevel MIN_SUPPORTED = AndroidApiLevel.O;
  private static final String EXPECTED_RESULT =
      StringUtils.lines(
          "FP:--05-05;--06-06",
          "FP:--05-05;--06-06",
          "FP:--05-05;--06-0601",
          "MFP:--05-05;--06-06");

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getConversionParametersUpToExcluding(MIN_SUPPORTED),
        getJdk8Jdk11(),
        SPECIFICATIONS_WITH_CF2CF);
  }

  public FreezePeriodTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  @Test
  public void testFreezePeriod() throws Exception {
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addProgramClasses(Executor.class, MyFreezePeriod.class)
        .setCustomLibrarySpecification(
            new CustomLibrarySpecification(FreezePeriod.class, MIN_SUPPORTED))
        .addKeepMainRule(Executor.class)
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  static class Executor {

    public static void main(String[] args) {
      testConversion2ArgsOrLess();
      testConversion3ArgsOrMore();
      testConversionWithValuesOnStack();
      testConversionSuperInit();
    }

    private static void testConversionSuperInit() {
      MyFreezePeriod myFreezePeriod = new MyFreezePeriod(MonthDay.of(5, 5), MonthDay.of(6, 6));
      System.out.println(myFreezePeriod.print());
    }

    private static void testConversionWithValuesOnStack() {
      print(0, new FreezePeriod(MonthDay.of(5, 5), MonthDay.of(6, 6)), 1);
    }

    private static void testConversion3ArgsOrMore() {
      FreezePeriod freezePeriod2 = new FreezePeriod(MonthDay.of(5, 5), MonthDay.of(6, 6), 0, 1);
      System.out.println(freezePeriod2.print());
    }

    private static void testConversion2ArgsOrLess() {
      FreezePeriod freezePeriod = new FreezePeriod(MonthDay.of(5, 5), MonthDay.of(6, 6));
      System.out.println(freezePeriod.print());
    }

    static void print(int i1, FreezePeriod freezePeriod, int i2) {
      System.out.println(freezePeriod.print() + i1 + i2);
    }
  }

  static class MyFreezePeriod extends FreezePeriod {

    public MyFreezePeriod(MonthDay start, MonthDay end) {
      super(start, end);
    }

    public String print() {
      return "M" + super.print();
    }
  }

  // This class will be put at compilation time as library and on the runtime class path.
  // This class is convenient for easy testing. Each method plays the role of methods in the
  // platform APIs for which argument/return values need conversion.
  static class FreezePeriod {

    private final MonthDay start;
    private final MonthDay end;

    public FreezePeriod(MonthDay start, MonthDay end) {
      this.start = start;
      this.end = end;
    }

    public FreezePeriod(MonthDay start, MonthDay end, int extra1, Integer extra2) {
      this.start = start;
      this.end = end;
    }

    public String print() {
      return "FP:" + start + ";" + end;
    }
  }
}
