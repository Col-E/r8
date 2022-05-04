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
import java.util.function.Supplier;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class FunctionConversionTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  private static final AndroidApiLevel MIN_SUPPORTED = AndroidApiLevel.N;
  private static final String EXPECTED_RESULT =
      StringUtils.lines(" true true true", "2", "false", "3", "true", "5", "42.0");

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getConversionParametersUpToExcluding(MIN_SUPPORTED),
        getJdk8Jdk11(),
        SPECIFICATIONS_WITH_CF2CF);
  }

  public FunctionConversionTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  @Test
  public void testFunction() throws Throwable {
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addProgramClasses(
            Executor.class, Executor.Object1.class, Executor.Object2.class, Executor.Object3.class)
        .setCustomLibrarySpecification(
            new CustomLibrarySpecification(CustomLibClass.class, MIN_SUPPORTED))
        .addKeepMainRule(Executor.class)
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  static class Executor {

    public static void main(String[] args) {
      Function<Object1, Object3> function = CustomLibClass.mixFunction(Object2::new, Object3::new);
      System.out.println(function.apply(new Object1()).toString());
      BiFunction<String, String, Character> biFunction =
          CustomLibClass.mixBiFunctions((String i, String j) -> i + j, (String s) -> s.charAt(1));
      System.out.println(biFunction.apply("1", "2"));
      BooleanSupplier booleanSupplier =
          () -> CustomLibClass.mixBoolSuppliers(() -> true, () -> false).get();
      System.out.println(booleanSupplier.getAsBoolean());
      LongConsumer longConsumer = CustomLibClass.mixLong(() -> 1L, System.out::println);
      longConsumer.accept(2L);
      DoublePredicate doublePredicate =
          CustomLibClass.mixPredicate(d -> d > 1.0, d -> d == 2.0, d -> d < 3.0);
      System.out.println(doublePredicate.test(2.0));
      System.out.println(CustomLibClass.extractInt(() -> 5));
      System.out.println(CustomLibClass.getDoubleSupplier().get());
    }

    static class Object1 {}

    static class Object2 {

      private Object1 field;

      Object2(Object1 o) {
        this.field = o;
      }
    }

    static class Object3 {

      private Object2 field;

      Object3(Object2 o) {
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

    // BooleanSupplier is not a wrapped type, so it can't be placed on the boundary.
    public static Supplier<Boolean> mixBoolSuppliers(
        Supplier<Boolean> supplier1, Supplier<Boolean> supplier2) {
      BooleanSupplier wrap1 = supplier1::get;
      BooleanSupplier wrap2 = supplier2::get;
      return () -> wrap1.getAsBoolean() && wrap2.getAsBoolean();
    }

    // LongSupplier is not a wrapped type, so it can't be placed on the boundary.
    public static LongConsumer mixLong(Supplier<Long> supplier, LongConsumer consumer) {
      LongSupplier wrap = supplier::get;
      return l -> consumer.accept(l + wrap.getAsLong());
    }

    public static DoublePredicate mixPredicate(
        DoublePredicate predicate1, DoublePredicate predicate2, DoublePredicate predicate3) {
      return predicate1.and(predicate2).and(predicate3);
    }

    // IntSupplier is not a wrapped type, so it can't be placed on the boundary.
    public static int extractInt(Supplier<Integer> supplier) {
      IntSupplier wrap = supplier::get;
      return wrap.getAsInt();
    }

    // DoubleSupplier is not a wrapped type, so it can't be placed on the boundary.
    public static Supplier<Double> getDoubleSupplier() {
      DoubleSupplier supplier = () -> 42.0;
      return supplier::getAsDouble;
    }
  }
}
