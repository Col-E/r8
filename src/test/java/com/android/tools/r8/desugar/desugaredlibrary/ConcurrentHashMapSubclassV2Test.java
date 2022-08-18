// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
//  for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.DEFAULT_SPECIFICATIONS;
import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.R8_L8SHRINK;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ConcurrentHashMapSubclassV2Test extends DesugaredLibraryTestBase {

  private static final String EXPECTED_RESULT = StringUtils.lines("foo");

  private final TestParameters parameters;
  private final CompilationSpecification compilationSpecification;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationMode compilationMode;

  @Parameters(name = "{0}, spec: {1}, {2}, {3}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(),
        getJdk8Jdk11(),
        DEFAULT_SPECIFICATIONS,
        CompilationMode.values());
  }

  public ConcurrentHashMapSubclassV2Test(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification,
      CompilationMode compilationMode) {
    this.parameters = parameters;
    this.compilationSpecification = compilationSpecification;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationMode = compilationMode;
  }

  private boolean isDefinedOnBootClasspath() {
    return parameters
        .getRuntime()
        .asDex()
        .getMinApiLevel()
        .isGreaterThanOrEqualTo(AndroidApiLevel.N);
  }

  @Test
  public void test() throws Throwable {
    Assume.assumeTrue("b/237701688", compilationSpecification == R8_L8SHRINK);
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addInnerClasses(getClass())
        .addKeepMainRule(Executor.class)
        .overrideCompilationMode(compilationMode)
        .addOptionsModification(
            options -> {
              // Devirtualizing is correcting the invalid member-rebinding.
              if (compilationMode.isRelease()) {
                options.enableDevirtualization = false;
              }
            })
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  static class Executor {
    public static void main(String[] args) {
      StringListConcurrentHashMap map = new StringListConcurrentHashMap();
      map.putIfAbsent("foo", "bar");
      System.out.println(map.keys().nextElement());
    }
  }

  static class StringListConcurrentHashMap extends ConcurrentHashMap<String, String> {}
}
