// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.conversiontests;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.D8_L8DEBUG;
import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.DEFAULT_SPECIFICATIONS;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_LEGACY;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_MINIMAL;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_PATH;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK8;
import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.CustomLibrarySpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class FlatMapConversionTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  private static final AndroidApiLevel MIN_SUPPORTED = AndroidApiLevel.N;
  private static final String EXPECTED_RESULT =
      StringUtils.lines(
          "[1, 2, 3]",
          "[1, 2, 3]",
          "[1.0, 2.0, 3.0]",
          "[1, 2, 3]",
          "[1, 2, 3]",
          "[1.0, 2.0, 3.0]",
          "[1, 2, 3]");

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getConversionParametersUpToExcluding(MIN_SUPPORTED),
        ImmutableList.of(JDK8, JDK11_LEGACY, JDK11_MINIMAL, JDK11, JDK11_PATH),
        DEFAULT_SPECIFICATIONS);
  }

  public FlatMapConversionTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  @Test
  public void testReference() throws Throwable {
    Assume.assumeTrue(
        "Run only once",
        libraryDesugaringSpecification == JDK11 && compilationSpecification == D8_L8DEBUG);
    testForD8()
        .setMinApi(parameters.getApiLevel())
        .addProgramClasses(Executor.class, CustomLibClass.class)
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testConvert() throws Throwable {
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addProgramClasses(Executor.class)
        .setCustomLibrarySpecification(
            new CustomLibrarySpecification(CustomLibClass.class, MIN_SUPPORTED))
        .addKeepMainRule(Executor.class)
        .run(parameters.getRuntime(), Executor.class)
        .applyIf(
            libraryDesugaringSpecification == JDK8
                || libraryDesugaringSpecification == JDK11_LEGACY,
            r ->
                r.assertFailureWithErrorThatMatches(containsString("java.lang.ClassCastException")),
            r -> r.assertSuccessWithOutput(EXPECTED_RESULT));
  }

  static class Executor {

    public static void main(String[] args) {
      System.out.println(
          Arrays.toString(CustomLibClass.getIntStreamAsStream().flatMap(Stream::of).toArray()));

      System.out.println(
          Arrays.toString(
              CustomLibClass.getIntStreamAsStream().flatMapToInt(IntStream::of).toArray()));
      System.out.println(
          Arrays.toString(
              CustomLibClass.getDoubleStreamAsStream()
                  .flatMapToDouble(DoubleStream::of)
                  .toArray()));
      System.out.println(
          Arrays.toString(
              CustomLibClass.getLongStreamAsStream().flatMapToLong(LongStream::of).toArray()));

      System.out.println(
          Arrays.toString(CustomLibClass.getIntStream().flatMap(IntStream::of).toArray()));
      System.out.println(
          Arrays.toString(CustomLibClass.getDoubleStream().flatMap(DoubleStream::of).toArray()));
      System.out.println(
          Arrays.toString(CustomLibClass.getLongStream().flatMap(LongStream::of).toArray()));
    }
  }

  // This class will be put at compilation time as library and on the runtime class path.
  // This class is convenient for easy testing. Each method plays the role of methods in the
  // platform APIs for which argument/return values need conversion.
  static class CustomLibClass {

    public static Stream<Integer> getIntStreamAsStream() {
      return Arrays.stream(new Integer[] {1, 2, 3});
    }

    public static Stream<Double> getDoubleStreamAsStream() {
      return Arrays.stream(new Double[] {1.0, 2.0, 3.0});
    }

    public static Stream<Long> getLongStreamAsStream() {
      return Arrays.stream(new Long[] {1L, 2L, 3L});
    }

    public static IntStream getIntStream() {
      return Arrays.stream(new int[] {1, 2, 3});
    }

    public static DoubleStream getDoubleStream() {
      return Arrays.stream(new double[] {1.0, 2.0, 3.0});
    }

    public static LongStream getLongStream() {
      return Arrays.stream(new long[] {1L, 2L, 3L});
    }
  }
}
