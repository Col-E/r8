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
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AllOptionalConversionTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  private static final AndroidApiLevel MIN_SUPPORTED = AndroidApiLevel.N;
  private static final String EXPECTED_RESULT =
      StringUtils.lines(
          "Optional[value]",
          "OptionalDouble[1.0]",
          "OptionalInt[1]",
          "OptionalLong[1]",
          "Optional[value]",
          "value");

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getConversionParametersUpToExcluding(MIN_SUPPORTED),
        getJdk8Jdk11(),
        DEFAULT_SPECIFICATIONS);
  }

  public AllOptionalConversionTest(
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
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  static class Executor {

    public static void main(String[] args) {
      returnValueUsed();
      returnValueUnused();
      virtualMethods();
    }

    @SuppressWarnings("all")
    public static void returnValueUsed() {
      System.out.println(CustomLibClass.mix(Optional.empty(), Optional.of("value")));
      System.out.println(CustomLibClass.mix(OptionalDouble.empty(), OptionalDouble.of(1.0)));
      System.out.println(CustomLibClass.mix(OptionalInt.empty(), OptionalInt.of(1)));
      System.out.println(CustomLibClass.mix(OptionalLong.empty(), OptionalLong.of(1L)));
    }

    @SuppressWarnings("all")
    public static void returnValueUnused() {
      CustomLibClass.mix(Optional.empty(), Optional.of("value"));
      CustomLibClass.mix(OptionalDouble.empty(), OptionalDouble.of(1.0));
      CustomLibClass.mix(OptionalInt.empty(), OptionalInt.of(1));
      CustomLibClass.mix(OptionalLong.empty(), OptionalLong.of(1L));
    }

    public static void virtualMethods() {
      CustomLibClass customLibClass = new CustomLibClass();
      Optional<String> optionalValue = Optional.of("value");
      customLibClass.virtual(optionalValue);
      customLibClass.virtualString(optionalValue);
      System.out.println(customLibClass.virtual(optionalValue));
      System.out.println(customLibClass.virtualString(optionalValue));
    }
  }

  // This class will be put at compilation time as library and on the runtime class path.
  // This class is convenient for easy testing. Each method plays the role of methods in the
  // platform APIs for which argument/return values need conversion.
  static class CustomLibClass {

    @SuppressWarnings("all")
    public static <T> Optional<T> mix(Optional<T> optional1, Optional<T> optional2) {
      return optional1.isPresent() ? optional1 : optional2;
    }

    @SuppressWarnings("all")
    public static OptionalDouble mix(OptionalDouble optional1, OptionalDouble optional2) {
      return optional1.isPresent() ? optional1 : optional2;
    }

    @SuppressWarnings("all")
    public static OptionalInt mix(OptionalInt optional1, OptionalInt optional2) {
      return optional1.isPresent() ? optional1 : optional2;
    }

    @SuppressWarnings("all")
    public static OptionalLong mix(OptionalLong optional1, OptionalLong optional2) {
      return optional1.isPresent() ? optional1 : optional2;
    }

    @SuppressWarnings("all")
    public Optional<String> virtual(Optional<String> optional) {
      return optional;
    }

    @SuppressWarnings("all")
    public String virtualString(Optional<String> optional) {
      return optional.get();
    }
  }
}
