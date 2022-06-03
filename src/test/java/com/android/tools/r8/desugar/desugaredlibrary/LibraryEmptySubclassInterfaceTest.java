// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
//  for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.DEFAULT_SPECIFICATIONS;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class LibraryEmptySubclassInterfaceTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(),
        getJdk8Jdk11(),
        DEFAULT_SPECIFICATIONS);
  }

  public LibraryEmptySubclassInterfaceTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  @Test
  public void testEmptySubclassInterface() throws Throwable {
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addInnerClasses(getClass())
        .addKeepMainRule(Executor.class)
        .noMinification()
        .compile()
        .inspectKeepRules(this::assertExpectedKeepRules)
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutputLines(getResult());
  }

  private void assertExpectedKeepRules(List<String> keepRuleList) {
    if (!libraryDesugaringSpecification.hasEmulatedInterfaceDesugaring(parameters)) {
      return;
    }
    StringBuilder keepRules = new StringBuilder();
    for (String kr : keepRuleList) {
      keepRules.append("\n").append(kr);
    }
    assertThat(
        keepRules.toString(), containsString("-keep class j$.util.concurrent.ConcurrentHashMap"));
  }

  private String getResult() {
    return libraryDesugaringSpecification.hasEmulatedInterfaceDesugaring(parameters)
        ? "class j$.util.concurrent.ConcurrentHashMap"
        : "class java.util.concurrent.ConcurrentHashMap";
  }

  @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
  static class Executor {

    public static void main(String[] args) {
      System.out.println(NullableConcurrentHashMap.class.getSuperclass());
    }
  }

  static class NullableConcurrentHashMap<K, V> extends ConcurrentHashMap<K, V> {
    NullableConcurrentHashMap() {
      super();
    }
  }
}
