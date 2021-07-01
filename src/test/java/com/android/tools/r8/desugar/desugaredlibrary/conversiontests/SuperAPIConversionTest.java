// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.desugaredlibrary.conversiontests;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SuperAPIConversionTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final boolean shrinkDesugaredLibrary;

  private static final AndroidApiLevel MIN_SUPPORTED = AndroidApiLevel.N;

  private static Path CUSTOM_LIB;

  @Parameters(name = "{0}, shrinkDesugaredLibrary: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getConversionParametersUpToExcluding(MIN_SUPPORTED), BooleanUtils.values());
  }

  public SuperAPIConversionTest(TestParameters parameters, boolean shrinkDesugaredLibrary) {
    this.shrinkDesugaredLibrary = shrinkDesugaredLibrary;
    this.parameters = parameters;
  }

  @BeforeClass
  public static void compileCustomLib() throws Exception {
    CUSTOM_LIB =
        testForD8(getStaticTemp())
            .addProgramClasses(CustomLibClass.class)
            .setMinApi(MIN_SUPPORTED)
            .compile()
            .writeToZip();
  }

  @Test
  public void testAPIConversionNoDesugaring() throws Exception {
    Assume.assumeTrue("No need to test twice", shrinkDesugaredLibrary);
    testForD8()
        .addInnerClasses(SuperAPIConversionTest.class)
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutputLines("Head");
  }

  @Test
  public void testAPIConversionDesugaringD8() throws Exception {
    Assume.assumeFalse("TODO(b/189435770): fix", shrinkDesugaredLibrary);
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    testForD8()
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
        .addProgramClasses(Executor.class, ParallelRandom.class)
        .setMinApi(parameters.getApiLevel())
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .compile()
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibrary,
            parameters.getApiLevel(),
            keepRuleConsumer.get(),
            shrinkDesugaredLibrary)
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutputLines("IntStream$VivifiedWrapper");
  }

  @Test
  public void testAPIConversionDesugaringR8() throws Exception {
    Assume.assumeFalse("TODO(b/189435770): fix", shrinkDesugaredLibrary);
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    testForR8(parameters.getBackend())
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
        .addProgramClasses(Executor.class, ParallelRandom.class)
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(Executor.class)
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .compile()
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibrary,
            parameters.getApiLevel(),
            keepRuleConsumer.get(),
            shrinkDesugaredLibrary)
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutputLines("IntStream$VivifiedWrapper");
  }

  @Test
  public void testAPIConversionDesugaringD8B192351030() throws Exception {
    Assume.assumeFalse("TODO(b/189435770): fix", shrinkDesugaredLibrary);
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    testForD8()
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
        .addLibraryClasses(CustomLibClass.class)
        .addProgramClasses(ExecutorB192351030.class, A.class, B.class, C.class)
        .setMinApi(parameters.getApiLevel())
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .compile()
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibrary,
            parameters.getApiLevel(),
            keepRuleConsumer.get(),
            shrinkDesugaredLibrary)
        .addRunClasspathFiles(CUSTOM_LIB)
        .run(parameters.getRuntime(), ExecutorB192351030.class)
        .assertSuccessWithOutputLines("Hello, ", "world!", "C");
  }

  @Test
  public void testAPIConversionDesugaringR8B192351030() throws Exception {
    Assume.assumeFalse("TODO(b/189435770): fix", shrinkDesugaredLibrary);
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    testForR8(parameters.getBackend())
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
        .addLibraryClasses(CustomLibClass.class)
        .addProgramClasses(ExecutorB192351030.class, A.class, B.class, C.class)
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(ExecutorB192351030.class)
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .compile()
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibrary,
            parameters.getApiLevel(),
            keepRuleConsumer.get(),
            shrinkDesugaredLibrary)
        .addRunClasspathFiles(CUSTOM_LIB)
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
