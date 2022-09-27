// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.DEFAULT_SPECIFICATIONS;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.CustomLibrarySpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DontKeepBootstrapClassesTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final CompilationSpecification compilationSpecification;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(),
        getJdk8Jdk11(),
        DEFAULT_SPECIFICATIONS);
  }

  public DontKeepBootstrapClassesTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.compilationSpecification = compilationSpecification;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
  }

  @Test
  public void testDontKeep() throws Throwable {
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addProgramClasses(TestClass.class)
        .addKeepMainRule(TestClass.class)
        .setCustomLibrarySpecification(
            new CustomLibrarySpecification(CustomLibClass.class, AndroidApiLevel.N))
        .compile()
        .inspectKeepRules(
            keepRule -> {
              if (libraryDesugaringSpecification.hasEmulatedInterfaceDesugaring(parameters)) {
                String prefix = libraryDesugaringSpecification.functionPrefix(parameters);
                assertTrue(
                    keepRule.stream()
                        .anyMatch(
                            kr ->
                                kr.contains("-keep class " + prefix + ".util.function.Consumer")));
              }
            });
  }

  static class CustomLibClass {

    public static <T> Consumer<T> id(Consumer<T> fn) {
      return fn;
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      Arrays.asList("A", "B", "C").forEach(CustomLibClass.id(System.out::println));
    }
  }
}
