// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.corelib.conversionTests;

import com.android.tools.r8.TestRuntime.DexRuntime;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Path;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.BooleanSupplier;
import java.util.function.DoublePredicate;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import org.junit.Test;

public class FunctionConversionsTest extends APIConversionTestBase {

  @Test
  public void testFunctionComposition() throws Exception {
    Path customLib = testForD8().addProgramClasses(CustomLibClass.class).compile().writeToZip();
    testForD8()
        .setMinApi(AndroidApiLevel.B)
        .addProgramClasses(
            Executor.class, Executor.Object1.class, Executor.Object2.class, Executor.Object3.class)
        .addLibraryClasses(CustomLibClass.class)
        .enableCoreLibraryDesugaring(AndroidApiLevel.B)
        .compile()
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibraryWithConversionExtension, AndroidApiLevel.B)
        .addRunClasspathFiles(customLib)
        .run(new DexRuntime(DexVm.ART_9_0_0_HOST), Executor.class)
        .assertSuccessWithOutput(
            StringUtils.lines("Object1 Object2 Object3", "2", "false", "3", "true"));
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
        return field.field.getClass().getSimpleName()
            + " "
            + field.getClass().getSimpleName()
            + " "
            + getClass().getSimpleName();
      }
    }
  }

  // This class will be put at compilation time as library and on the runtime class path.
  // This class is convenient for easy testing. None of the methods make sense.
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
  }
}
