// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.SPECIFICATIONS_WITH_CF2CF;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.StringUtils;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CustomMapHierarchyTest extends DesugaredLibraryTestBase {

  private static final String EXPECTED =
      StringUtils.lines("B::getOrDefault", "default 1", "B::getOrDefault", "default 2");

  private final TestParameters parameters;
  private final CompilationSpecification compilationSpecification;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build(),
        getJdk8Jdk11(),
        SPECIFICATIONS_WITH_CF2CF);
  }

  public CustomMapHierarchyTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.compilationSpecification = compilationSpecification;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
  }

  @Test
  public void testCollection() throws Throwable {
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepAllClassesRuleWithAllowObfuscation()
        .addKeepAttributes(
            ProguardKeepAttributes.SIGNATURE,
            ProguardKeepAttributes.INNER_CLASSES,
            ProguardKeepAttributes.ENCLOSING_METHOD)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testWithoutLibraryDesugaring() throws Exception {
    Assume.assumeTrue(
        parameters.getRuntime().isCf()
            || parameters
                .getApiLevel()
                .isGreaterThanOrEqualTo(TestBase.apiLevelWithDefaultInterfaceMethodsSupport()));
    testForRuntime(parameters)
        .addInnerClasses(CustomMapHierarchyTest.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  public static class Main {
    public static void main(String[] args) {
      System.out.println(new B<String>().getOrDefault("Not found", "default 1"));
      System.out.println(
          ((Map<String, String>) new B<String>()).getOrDefault("Not found", "default 2"));
    }
  }

  abstract static class A<T> extends LinkedHashMap<T, T> {}

  // AbstractSequentialList implements List further up the hierarchy.
  static class B<T> extends A<T> {
    // Need at least one overridden default method for emulated dispatch.
    @Override
    public T getOrDefault(Object key, T defaultValue) {
      System.out.println("B::getOrDefault");
      return super.getOrDefault(key, defaultValue);
    }
  }
}
