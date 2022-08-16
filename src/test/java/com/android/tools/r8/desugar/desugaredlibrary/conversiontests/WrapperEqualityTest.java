// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.conversiontests;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.DEFAULT_SPECIFICATIONS;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.CustomLibrarySpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class WrapperEqualityTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  private static final AndroidApiLevel MIN_SUPPORTED = AndroidApiLevel.N;
  private static final String EXPECTED_RESULT =
      StringUtils.lines(
          "true", "true", "true", "true", "true", "true", "true", "true", "false", "false", "1",
          "1", "2", "2", "1", "1", "1", "0", "true", "true", "true", "true");
  private static final String DESUGARED_LIBRARY_EXPECTED_RESULT =
      StringUtils.lines(
          "true", "true", "true", "true", "false", "true", "false", "true", "false", "false", "1",
          "1", "2", "2", "1", "1", "1", "0", "false", "true", "false", "true");

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getConversionParametersUpToExcluding(MIN_SUPPORTED),
        getJdk8Jdk11(),
        DEFAULT_SPECIFICATIONS);
  }

  public WrapperEqualityTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  @Test
  public void test() throws Throwable {
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addProgramClasses(Executor.class)
        .setCustomLibrarySpecification(
            new CustomLibrarySpecification(CustomLibClass.class, MIN_SUPPORTED))
        .addKeepMainRule(Executor.class)
        .compile()
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutput(
            libraryDesugaringSpecification.hasJDollarFunction(parameters)
                ? DESUGARED_LIBRARY_EXPECTED_RESULT
                : EXPECTED_RESULT);
  }

  @Test
  public void testD8() throws Throwable {
    // Run a D8 test without desugared library on all runtimes which natively supports
    // java.util.function to ensure the expectations. The API level check is just to not run the
    // same test repeatedly.
    assertEquals(AndroidApiLevel.N, MIN_SUPPORTED);
    assumeTrue(
        parameters.getApiLevel().isEqualTo(AndroidApiLevel.M)
            && parameters.isDexRuntime()
            && parameters.asDexRuntime().getVersion().isNewerThanOrEqual(Version.V8_1_0)
            && compilationSpecification == CompilationSpecification.D8_L8DEBUG);
    testForD8(parameters.getBackend())
        .addProgramClasses(Executor.class, CustomLibClass.class)
        .compile()
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  static class Executor {

    public static void main(String[] args) {
      Consumer<Boolean> consumer = b -> {};
      Supplier<Boolean> supplier = () -> Boolean.TRUE;
      // Prints true for desugared library as the same wrapper is used for both arguments.
      System.out.println(CustomLibClass.same(consumer, consumer));
      System.out.println(CustomLibClass.equals(consumer, consumer));
      // Prints true for desugared library as the same wrapper is used for both arguments.
      System.out.println(CustomLibClass.same(supplier, supplier));
      System.out.println(CustomLibClass.equals(supplier, supplier));

      CustomLibClass.setConsumer(consumer);
      CustomLibClass.setSupplier(supplier);
      System.out.println(CustomLibClass.same(consumer));
      System.out.println(CustomLibClass.equals(consumer));
      System.out.println(CustomLibClass.same(supplier));
      System.out.println(CustomLibClass.equals(supplier));
      System.out.println(CustomLibClass.equalsWithObject(consumer, new HashMap<>()));
      System.out.println(CustomLibClass.equalsWithObject(supplier, new ArrayList<>()));

      CustomLibClass.register(consumer, new Object());
      System.out.println(CustomLibClass.registrations());
      CustomLibClass.register(consumer, new Object());
      System.out.println(CustomLibClass.registrations());
      CustomLibClass.register(supplier, new Object());
      System.out.println(CustomLibClass.registrations());
      CustomLibClass.register(supplier, new Object());
      System.out.println(CustomLibClass.registrations());
      System.out.println(CustomLibClass.suppliers());
      System.out.println(CustomLibClass.consumers());
      CustomLibClass.unregister(consumer);
      System.out.println(CustomLibClass.registrations());
      CustomLibClass.unregister(supplier);
      System.out.println(CustomLibClass.registrations());

      // Prints false for desugared library as wrappers does not keep identity.
      System.out.println(
          CustomLibClass.getConsumerFromPlatform() == CustomLibClass.getConsumerFromPlatform());
      System.out.println(
          CustomLibClass.getConsumerFromPlatform()
              .equals(CustomLibClass.getConsumerFromPlatform()));
      // Prints false for desugared library as wrappers does not keep identity.
      System.out.println(
          CustomLibClass.getSupplierFromPlatform() == CustomLibClass.getSupplierFromPlatform());
      System.out.println(
          CustomLibClass.getSupplierFromPlatform()
              .equals(CustomLibClass.getSupplierFromPlatform()));
    }
  }

  // This class will be put at compilation time as library and on the runtime class path.
  // This class is convenient for easy testing. Each method plays the role of methods in the
  // platform APIs for which argument/return values need conversion.
  static class CustomLibClass {
    private static final Map<Object, Object> map = new HashMap<>();
    private static final Consumer<Boolean> consumer = b -> {};
    private static final Supplier<Boolean> supplier = () -> Boolean.TRUE;

    private static Consumer<Boolean> appConsumer;
    private static Supplier<Boolean> appSupplier;

    public static boolean equals(Consumer<Boolean> consumer1, Consumer<Boolean> consumer2) {
      return consumer1.equals(consumer2) && consumer2.equals(consumer1);
    }

    public static boolean equals(Supplier<Boolean> supplier1, Supplier<Boolean> supplier2) {
      return supplier1.equals(supplier2) && supplier2.equals(supplier1);
    }

    public static boolean same(Consumer<Boolean> consumer1, Consumer<Boolean> consumer2) {
      return consumer1.equals(consumer2) && consumer2.equals(consumer1);
    }

    public static boolean same(Supplier<Boolean> supplier1, Supplier<Boolean> supplier2) {
      return supplier1.equals(supplier2) && supplier2.equals(supplier1);
    }

    public static void setConsumer(Consumer<Boolean> consumer) {
      appConsumer = consumer;
    }

    @SuppressWarnings("unchecked")
    public static void setSupplier(Supplier supplier) {
      appSupplier = supplier;
    }

    public static boolean equals(Consumer<Boolean> consumer) {
      return consumer.equals(appConsumer) && appConsumer.equals(consumer);
    }

    public static boolean equals(Supplier<Boolean> supplier) {
      return supplier.equals(appSupplier) && appSupplier.equals(supplier);
    }

    public static boolean same(Consumer<Boolean> consumer) {
      return appConsumer == consumer;
    }

    public static boolean same(Supplier supplier) {
      return appSupplier == supplier;
    }

    public static boolean equalsWithObject(Consumer<Boolean> consumer, Object object) {
      return consumer.equals(object);
    }

    public static boolean equalsWithObject(Supplier<Boolean> supplier, Object object) {
      return supplier.equals(object);
    }

    public static void register(Consumer<Boolean> listener, Object context) {
      map.put(listener, context);
    }

    public static void unregister(Consumer<Boolean> listener) {
      map.remove(listener);
    }

    public static void register(Supplier<Boolean> listener, Object context) {
      map.put(listener, context);
    }

    public static void unregister(Supplier<Boolean> listener) {
      map.remove(listener);
    }

    public static int registrations() {
      return map.size();
    }

    public static long consumers() {
      return map.keySet().stream().filter(k -> k instanceof Consumer).count();
    }

    public static long suppliers() {
      return map.keySet().stream().filter(k -> k instanceof Supplier).count();
    }

    public static Supplier<Boolean> getSupplierFromPlatform() {
      return supplier;
    }

    public static Consumer<Boolean> getConsumerFromPlatform() {
      return consumer;
    }
  }
}
