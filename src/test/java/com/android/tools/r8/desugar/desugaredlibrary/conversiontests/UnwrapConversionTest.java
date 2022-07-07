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
import java.util.List;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import java.util.stream.BaseStream;
import java.util.stream.IntStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class UnwrapConversionTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  private static final AndroidApiLevel MIN_SUPPORTED = AndroidApiLevel.N;
  private static final String EXPECTED_RESULT = StringUtils.lines("true", "true", "true", "true");

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getConversionParametersUpToExcluding(MIN_SUPPORTED),
        getJdk8Jdk11(),
        DEFAULT_SPECIFICATIONS);
  }

  public UnwrapConversionTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  @Test
  public void testUnwrap() throws Exception {
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addProgramClasses(Executor.class)
        .addKeepMainRule(Executor.class)
        .setCustomLibrarySpecification(
            new CustomLibrarySpecification(CustomLibClass.class, MIN_SUPPORTED))
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  static class Executor {

    @SuppressWarnings("all")
    public static void main(String[] args) {
      consumerTest();
      streamTest();
    }

    private static void streamTest() {
      // Type wrapper.
      IntStream intStream = Arrays.stream(new int[] {1});
      BaseStream<?, ?> unwrapped = CustomLibClass.identity(intStream);
      System.out.println(unwrapped == intStream);

      // Vivified wrapper.
      IntStream consumer = CustomLibClass.getStream();
      System.out.println(CustomLibClass.testStream(consumer));
    }

    private static void consumerTest() {
      // Type wrapper.
      IntConsumer intConsumer = i -> {};
      IntConsumer unwrappedIntConsumer = CustomLibClass.identity(intConsumer);
      System.out.println(intConsumer == unwrappedIntConsumer);

      // Vivified wrapper.
      DoubleConsumer consumer = CustomLibClass.getConsumer();
      System.out.println(CustomLibClass.testConsumer(consumer));
    }
  }

  // This class will be put at compilation time as library and on the runtime class path.
  // This class is convenient for easy testing. Each method plays the role of methods in the
  // platform APIs for which argument/return values need conversion.
  static class CustomLibClass {

    private static DoubleConsumer consumer = d -> {};

    @SuppressWarnings("WeakerAccess")
    public static IntConsumer identity(IntConsumer intConsumer) {
      return intConsumer;
    }

    public static DoubleConsumer getConsumer() {
      return consumer;
    }

    @SuppressWarnings("WeakerAccess")
    public static boolean testConsumer(DoubleConsumer doubleConsumer) {
      return doubleConsumer == consumer;
    }

    private static IntStream intStream = Arrays.stream(new int[] {0});

    @SuppressWarnings("WeakerAccess")
    public static BaseStream<Integer, IntStream> identity(IntStream arg) {
      return arg;
    }

    public static IntStream getStream() {
      return intStream;
    }

    @SuppressWarnings("WeakerAccess")
    public static boolean testStream(BaseStream<?, ?> stream) {
      return stream == intStream;
    }
  }
}
