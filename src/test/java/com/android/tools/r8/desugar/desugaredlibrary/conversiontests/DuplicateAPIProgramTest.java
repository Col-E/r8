// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.conversiontests;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.DEFAULT_SPECIFICATIONS;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.CustomLibrarySpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DuplicateAPIProgramTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  private static final AndroidApiLevel MIN_SUPPORTED = AndroidApiLevel.N;
  private static final String EXPECTED_RESULT = StringUtils.lines("ForEach 1 1.1", "ForEach 1 1.1");

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getConversionParametersUpToExcluding(MIN_SUPPORTED),
        getJdk8Jdk11(),
        DEFAULT_SPECIFICATIONS);
  }

  public DuplicateAPIProgramTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  @Test
  public void testMap() throws Throwable {
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addProgramClasses(Executor.class, MyMap.class)
        .setCustomLibrarySpecification(
            new CustomLibrarySpecification(CustomLibClass.class, MIN_SUPPORTED))
        .addKeepMainRule(Executor.class)
        .compile()
        .inspect(this::assertDupMethod)
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  private void assertDupMethod(CodeInspector i) {
    assertEquals(
        2,
        i.clazz(MyMap.class).virtualMethods().stream()
            .filter(m -> m.getOriginalName().equals("forEach"))
            .count());
  }

  static class Executor {

    public static void main(String[] args) {
      IdentityHashMap<Integer, Double> map = new MyMap<>();
      map.put(1, 1.1);
      BiConsumer<Integer, Double> biConsumer = (x, y) -> System.out.print(" " + x + " " + y);
      map.forEach(biConsumer);
      System.out.println();
      CustomLibClass.javaForEach(map, biConsumer);
      System.out.println();
    }
  }

  public static class MyMap<K, V> extends IdentityHashMap<K, V> {

    @Override
    public void forEach(BiConsumer<? super K, ? super V> action) {
      System.out.print("ForEach");
      super.forEach(action);
    }
  }

  // This class will be put at compilation time as library and on the runtime class path.
  // This class is convenient for easy testing. Each method plays the role of methods in the
  // platform APIs for which argument/return values need conversion.
  static class CustomLibClass {

    @SuppressWarnings("WeakerAccess")
    public static <K, V> void javaForEach(Map<K, V> map, BiConsumer<K, V> consumer) {
      map.forEach(consumer);
    }
  }
}
