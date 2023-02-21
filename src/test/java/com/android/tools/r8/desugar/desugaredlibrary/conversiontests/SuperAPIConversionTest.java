// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.desugaredlibrary.conversiontests;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.DEFAULT_SPECIFICATIONS;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK8;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.CustomLibrarySpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SuperAPIConversionTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  private static final AndroidApiLevel MIN_SUPPORTED = AndroidApiLevel.N;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getConversionParametersUpToExcluding(MIN_SUPPORTED),
        getJdk8Jdk11(),
        DEFAULT_SPECIFICATIONS);
  }

  public SuperAPIConversionTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  @Test
  public void testAPIConversionNoDesugaring() throws Exception {
    Assume.assumeTrue(
        "No need to test twice",
        libraryDesugaringSpecification == JDK8 && compilationSpecification.isProgramShrink());
    testForD8()
        .addInnerClasses(SuperAPIConversionTest.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutputLines("Head");
  }

  @Test
  public void testAPIConversion() throws Exception {
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addProgramClasses(Executor.class, ParallelRandom.class)
        .setCustomLibrarySpecification(
            new CustomLibrarySpecification(CustomLibClass.class, MIN_SUPPORTED))
        .addKeepMainRule(Executor.class)
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutputLines("IntStream$VivifiedWrapper");
  }

  @Test
  public void testAPIConversionB192351030() throws Exception {
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addProgramClasses(ExecutorB192351030.class, A.class, B.class, C.class)
        .setCustomLibrarySpecification(
            new CustomLibrarySpecification(CustomLibClass.class, MIN_SUPPORTED))
        .addKeepMainRule(ExecutorB192351030.class)
        .run(parameters.getRuntime(), ExecutorB192351030.class)
        .assertSuccessWithOutputLines("Hello, ", "world!", "C");
  }

  static class ParallelRandom extends Random {

    @Override
    public IntStream ints() {
      return super.ints().parallel();
    }
  }

  static class Executor {

    public static void main(String[] args) {
      IntStream intStream = new ParallelRandom().ints();
      System.out.println(intStream.getClass().getSimpleName());
    }
  }

  static class A extends CustomLibClass {
  }

  static class B extends A {}

  static class C extends B {
    void test(Consumer<String> consumer) {
      super.m(consumer);
    }

    public void m(Consumer<String> consumer) {
      consumer.accept("C");
    }
  }

  static class ExecutorB192351030 {

    public static void main(String[] args) {
      new C().test(System.out::println);
      new C().m(System.out::println);
    }
  }

  // This class will be put at compilation time as library and on the runtime class path.
  // This class is convenient for easy testing. Each method plays the role of methods in the
  // platform APIs for which argument/return values need conversion.
  static class CustomLibClass {

    public void m(Consumer<String> consumer) {
      consumer.accept("Hello, ");
      consumer.accept("world!");
    }
  }
}
