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
      StringUtils.lines("true", "true", "1", "1", "2", "2", "1", "1", "1", "0");
  // TODO(b/230800107): There should not be any unexpected results.
  private static final String UNEXPECTED_RESULT =
      StringUtils.lines("false", "false", "1", "2", "3", "4", "2", "2", "4", "4");

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
        .assertSuccessWithOutput(UNEXPECTED_RESULT);
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
      System.out.println(CustomLibClass.equals(consumer, consumer));
      System.out.println(CustomLibClass.equals(supplier, supplier));
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
    }
  }

  // This class will be put at compilation time as library and on the runtime class path.
  // This class is convenient for easy testing. Each method plays the role of methods in the
  // platform APIs for which argument/return values need conversion.
  static class CustomLibClass {
    static Map<Object, Object> map = new HashMap<>();

    public static boolean equals(Consumer<Boolean> listener1, Consumer<Boolean> listene2) {
      return listener1.equals(listene2) && listene2.equals(listener1);
    }

    public static boolean equals(Supplier<Boolean> listener1, Supplier<Boolean> listene2) {
      return listener1.equals(listene2) && listene2.equals(listener1);
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
  }
}
