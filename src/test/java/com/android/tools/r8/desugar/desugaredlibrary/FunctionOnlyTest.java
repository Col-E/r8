// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.L8Command;
import com.android.tools.r8.LibraryDesugaringTestConfiguration;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.StringResource;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibrarySpecificationParser;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoublePredicate;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class FunctionOnlyTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final boolean shrinkDesugaredLibrary;
  private static final String EXPECTED_RESULT =
      StringUtils.lines(" true true true", "2", "false", "3", "true", "5", "42.0");

  @Parameters(name = "{0}, shrinkDesugaredLibrary: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevelsAlsoForCf().build(),
        BooleanUtils.values());
  }

  public FunctionOnlyTest(TestParameters parameters, boolean shrinkDesugaredLibrary) {
    this.shrinkDesugaredLibrary = shrinkDesugaredLibrary;
    this.parameters = parameters;
  }

  @Test
  public void testFunctionCompositionD8() throws Exception {
    testForD8()
        .addLibraryFiles(getLibraryFile())
        .setMinApi(parameters.getApiLevel())
        .addInnerClasses(getClass())
        .enableCoreLibraryDesugaring(minimalConfiguration())
        .compile()
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  private LibraryDesugaringTestConfiguration minimalConfiguration() {
    if (!isJDK11DesugaredLibrary()) {
      return LibraryDesugaringTestConfiguration.builder()
          .setMinApi(parameters.getApiLevel())
          .setMode(CompilationMode.RELEASE)
          .withKeepRuleConsumer()
          .build();
    }
    return LibraryDesugaringTestConfiguration.builder()
        .setMinApi(parameters.getApiLevel())
        .addDesugaredLibraryConfiguration(
            StringResource.fromFile(ToolHelper.getDesugarLibJsonMinimalForTesting()))
        .setMode(shrinkDesugaredLibrary ? CompilationMode.RELEASE : CompilationMode.DEBUG)
        .withKeepRuleConsumer()
        .build();
  }

  @Test
  public void testFunctionCompositionD8Cf2Cf() throws Exception {
    Assume.assumeTrue(isJDK11DesugaredLibrary());
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    Path jar =
        testForD8(Backend.CF)
            .addLibraryFiles(getLibraryFile())
            .setMinApi(parameters.getApiLevel())
            .addInnerClasses(FunctionOnlyTest.class)
            .enableCoreLibraryDesugaring(
                parameters.getApiLevel(),
                keepRuleConsumer,
                StringResource.fromFile(ToolHelper.getDesugarLibJsonMinimalForTesting()))
            .compile()
            .writeToZip();
    String desugaredLibraryKeepRules = "";
    if (shrinkDesugaredLibrary && keepRuleConsumer.get() != null) {
      // Collection keep rules is only implemented in the DEX writer.
      assertEquals(0, keepRuleConsumer.get().length());
      desugaredLibraryKeepRules = "-keep class java.util.function.* { *; }";
    }
    if (parameters.getRuntime().isDex()) {
      testForD8()
          .addProgramFiles(jar)
          .setMinApi(parameters.getApiLevel())
          .disableDesugaring()
          .compile()
          .addDesugaredCoreLibraryRunClassPath(
              (api, keep, shrink) ->
                  buildDesugaredLibrary(
                      api,
                      keep,
                      shrink,
                      ImmutableList.of(),
                      opt ->
                          opt.setDesugaredLibrarySpecification(
                              DesugaredLibrarySpecificationParser
                                  .parseDesugaredLibrarySpecification(
                                      StringResource.fromFile(
                                          ToolHelper.getDesugarLibJsonMinimalForTesting()),
                                      opt.itemFactory,
                                      opt.reporter,
                                      true,
                                      parameters.getApiLevel().getLevel()))),
              parameters.getApiLevel(),
              desugaredLibraryKeepRules,
              shrinkDesugaredLibrary)
          .run(parameters.getRuntime(), Executor.class)
          .assertSuccessWithOutput(EXPECTED_RESULT);

    } else {
      testForJvm()
          .addProgramFiles(jar)
          .addRunClasspathFiles(getDesugaredLibraryInCFMinimal(parameters, options -> {}))
          .run(parameters.getRuntime(), Executor.class)
          .assertSuccessWithOutput(EXPECTED_RESULT);
    }
  }

  public Path getDesugaredLibraryInCFMinimal(
      TestParameters parameters, Consumer<InternalOptions> configurationForLibraryCompilation)
      throws IOException, CompilationFailedException {
    Path desugaredLib = temp.newFolder().toPath().resolve("desugar_jdk_libs.jar");
    L8Command.Builder l8Builder =
        L8Command.builder()
            .addLibraryFiles(getLibraryFile())
            .addProgramFiles(ToolHelper.getDesugarJDKLibs())
            .addProgramFiles(ToolHelper.DESUGAR_LIB_CONVERSIONS)
            .setMode(CompilationMode.DEBUG)
            .addDesugaredLibraryConfiguration(
                StringResource.fromFile(ToolHelper.getDesugarLibJsonMinimalForTesting()))
            .setMinApiLevel(parameters.getApiLevel().getLevel())
            .setOutput(desugaredLib, OutputMode.ClassFile);
    ToolHelper.runL8(l8Builder.build(), configurationForLibraryCompilation);
    return desugaredLib;
  }

  @Test
  public void testFunctionCompositionR8() throws Exception {
    testForR8(parameters.getBackend())
        .addLibraryFiles(getLibraryFile())
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(Executor.class)
        .addInnerClasses(getClass())
        .enableCoreLibraryDesugaring(minimalConfiguration())
        .allowStdoutMessages()
        .compile()
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  static class Executor {

    public static void main(String[] args) {
      Function<Executor.Object1, Executor.Object3> function =
          FunctionClass.composeFunction(Executor.Object2::new, Executor.Object3::new);
      System.out.println(function.apply(new Executor.Object1()).toString());
      BiFunction<String, String, Character> biFunction =
          FunctionClass.composeBiFunctions(
              (String i, String j) -> i + j, (String s) -> s.charAt(1));
      System.out.println(biFunction.apply("1", "2"));
      BooleanSupplier booleanSupplier =
          () -> FunctionClass.composeBoolSuppliers(() -> true, () -> false).get();
      System.out.println(booleanSupplier.getAsBoolean());
      LongConsumer longConsumer = FunctionClass.composeLong(() -> 1L, System.out::println);
      longConsumer.accept(2L);
      DoublePredicate doublePredicate =
          FunctionClass.composePredicate(d -> d > 1.0, d -> d == 2.0, d -> d < 3.0);
      System.out.println(doublePredicate.test(2.0));
      System.out.println(FunctionClass.extractInt(() -> 5));
      System.out.println(FunctionClass.getDoubleSupplier().get());
    }

    static class Object1 {}

    static class Object2 {

      private Executor.Object1 field;

      Object2(Executor.Object1 o) {
        this.field = o;
      }
    }

    static class Object3 {

      private Executor.Object2 field;

      Object3(Executor.Object2 o) {
        this.field = o;
      }

      @Override
      public String toString() {
        return " "
            + (field.field.getClass() == Executor.Object1.class)
            + " "
            + (field.getClass() == Executor.Object2.class)
            + " "
            + (getClass() == Executor.Object3.class);
      }
    }
  }

  static class FunctionClass {

    public static <T, Q, R> Function<T, R> composeFunction(Function<T, Q> f1, Function<Q, R> f2) {
      return f1.andThen(f2);
    }

    public static <T, R> BiFunction<T, T, R> composeBiFunctions(
        BinaryOperator<T> operator, Function<T, R> function) {
      return operator.andThen(function);
    }

    // BooleanSupplier is not a wrapped type, so it can't be placed on the boundary.
    public static Supplier<Boolean> composeBoolSuppliers(
        Supplier<Boolean> supplier1, Supplier<Boolean> supplier2) {
      BooleanSupplier wrap1 = supplier1::get;
      BooleanSupplier wrap2 = supplier2::get;
      return () -> wrap1.getAsBoolean() && wrap2.getAsBoolean();
    }

    // LongSupplier is not a wrapped type, so it can't be placed on the boundary.
    public static LongConsumer composeLong(Supplier<Long> supplier, LongConsumer consumer) {
      LongSupplier wrap = supplier::get;
      return l -> consumer.accept(l + wrap.getAsLong());
    }

    public static DoublePredicate composePredicate(
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
