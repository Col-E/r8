// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.conversiontests;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.ir.desugar.DesugaredLibraryWrapperSynthesizer;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.BooleanSupplier;
import java.util.function.DoublePredicate;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class FunctionConversionTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final boolean shrinkDesugaredLibrary;
  private static final AndroidApiLevel MIN_SUPPORTED = AndroidApiLevel.N;
  private static final String EXPECTED_RESULT =
      StringUtils.lines(" true true true", "2", "false", "3", "true", "5", "42.0");
  private static Path CUSTOM_LIB;

  @Parameters(name = "{0}, shrinkDesugaredLibrary: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getConversionParametersUpToExcluding(MIN_SUPPORTED), BooleanUtils.values());
  }

  public FunctionConversionTest(TestParameters parameters, boolean shrinkDesugaredLibrary) {
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
  public void testFunctionCompositionD8() throws Exception {
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    testForD8()
        .setMinApi(parameters.getApiLevel())
        .addProgramClasses(
            Executor.class, Executor.Object1.class, Executor.Object2.class, Executor.Object3.class)
        .addLibraryClasses(CustomLibClass.class)
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .compile()
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibrary,
            parameters.getApiLevel(),
            keepRuleConsumer.get(),
            shrinkDesugaredLibrary)
        .addRunClasspathFiles(CUSTOM_LIB)
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testFunctionCompositionR8() throws Exception {
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    testForR8(parameters.getBackend())
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(Executor.class)
        .addProgramClasses(
            Executor.class, Executor.Object1.class, Executor.Object2.class, Executor.Object3.class)
        .addLibraryClasses(CustomLibClass.class)
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .compile()
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibrary,
            parameters.getApiLevel(),
            keepRuleConsumer.get(),
            shrinkDesugaredLibrary)
        .addRunClasspathFiles(CUSTOM_LIB)
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testWrapperWithChecksum() throws Exception {
    Assume.assumeTrue(
        shrinkDesugaredLibrary && parameters.getApiLevel().getLevel() <= MIN_SUPPORTED.getLevel());
    testForD8()
        .addProgramClasses(
            Executor.class, Executor.Object1.class, Executor.Object2.class, Executor.Object3.class)
        .addLibraryClasses(CustomLibClass.class)
        .setMinApi(parameters.getApiLevel())
        .enableCoreLibraryDesugaring(parameters.getApiLevel())
        .setIncludeClassesChecksum(true) // Compilation fails if some classes are missing checksum.
        .compile()
        .inspect(
            inspector -> {
              Assert.assertEquals(
                  9,
                  inspector.allClasses().stream()
                      .filter(
                          clazz ->
                              clazz
                                  .getFinalName()
                                  .contains(DesugaredLibraryWrapperSynthesizer.TYPE_WRAPPER_SUFFIX))
                      .count());
              Assert.assertEquals(
                  9,
                  inspector.allClasses().stream()
                      .filter(
                          clazz ->
                              clazz
                                  .getFinalName()
                                  .contains(
                                      DesugaredLibraryWrapperSynthesizer
                                          .VIVIFIED_TYPE_WRAPPER_SUFFIX))
                      .count());
            });
  }

  static class Executor {

    public static void main(String[] args) {
      Function<Object1, Object3> function = CustomLibClass.mixFunction(Object2::new, Object3::new);
      System.out.println(function.apply(new Object1()).toString());
      BiFunction<String, String, Character> biFunction =
          CustomLibClass.mixBiFunctions((String i, String j) -> i + j, (String s) -> s.charAt(1));
      System.out.println(biFunction.apply("1", "2"));
      BooleanSupplier booleanSupplier = CustomLibClass.mixBoolSuppliers(() -> true, () -> false);
      System.out.println(booleanSupplier.getAsBoolean());
      LongConsumer longConsumer = CustomLibClass.mixLong(() -> 1L, System.out::println);
      longConsumer.accept(2L);
      DoublePredicate doublePredicate =
          CustomLibClass.mixPredicate(d -> d > 1.0, d -> d == 2.0, d -> d < 3.0);
      System.out.println(doublePredicate.test(2.0));
      // Reverse wrapper should not exist.
      System.out.println(CustomLibClass.extractInt(() -> 5));
      System.out.println(CustomLibClass.getDoubleSupplier().getAsDouble());
    }

    static class Object1 {}

    static class Object2 {

      private Object1 field;

      private Object2(Object1 o) {
        this.field = o;
      }
    }

    static class Object3 {

      private Object2 field;

      private Object3(Object2 o) {
        this.field = o;
      }

      @Override
      public String toString() {
        return " "
            + (field.field.getClass() == Object1.class)
            + " "
            + (field.getClass() == Object2.class)
            + " "
            + (getClass() == Object3.class);
      }
    }
  }

  // This class will be put at compilation time as library and on the runtime class path.
  // This class is convenient for easy testing. Each method plays the role of methods in the
  // platform APIs for which argument/return values need conversion.
  static class CustomLibClass {

    public static <T, Q, R> Function<T, R> mixFunction(Function<T, Q> f1, Function<Q, R> f2) {
      return f1.andThen(f2);
    }

    public static <T, R> BiFunction<T, T, R> mixBiFunctions(
        BinaryOperator<T> operator, Function<T, R> function) {
      return operator.andThen(function);
    }

    public static BooleanSupplier mixBoolSuppliers(
        BooleanSupplier supplier1, BooleanSupplier supplier2) {
      return () -> supplier1.getAsBoolean() && supplier2.getAsBoolean();
    }

    public static LongConsumer mixLong(LongSupplier supplier, LongConsumer consumer) {
      return l -> consumer.accept(l + supplier.getAsLong());
    }

    public static DoublePredicate mixPredicate(
        DoublePredicate predicate1, DoublePredicate predicate2, DoublePredicate predicate3) {
      return predicate1.and(predicate2).and(predicate3);
    }

    public static int extractInt(IntSupplier supplier) {
      return supplier.getAsInt();
    }

    public static DoubleSupplier getDoubleSupplier() {
      return () -> 42.0;
    }
  }
}
