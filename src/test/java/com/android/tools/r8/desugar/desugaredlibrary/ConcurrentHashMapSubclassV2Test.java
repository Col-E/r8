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
import com.android.tools.r8.utils.StringUtils;
import java.util.ArrayList;
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

  @Test
  public void test() throws Exception {
    Assume.assumeFalse(
        "b/237701688", compilationMode.isDebug() && compilationSpecification == R8_L8SHRINK);
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addInnerClasses(getClass())
        .setMode(compilationMode)
        .addKeepMainRule(Executor.class)
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @SuppressWarnings("unchecked")
  static class Executor {
    public static void main(String[] args) {
      StringListConcurrentHashMap<String> map = new StringListConcurrentHashMap<>();
      map.putKeyAndArray("foo");
      System.out.println(map.keys().nextElement());
    }
  }

  static class StringListConcurrentHashMap<K> extends ConcurrentHashMap<K, List<String>> {
    StringListConcurrentHashMap() {
      super();
    }

    void putKeyAndArray(K key) {
      this.putIfAbsent(key, new ArrayList<>());
    }
  }
}
