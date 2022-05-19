// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.DEFAULT_SPECIFICATIONS;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.StringUtils;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RequiredNonNullWithSupplierTest extends DesugaredLibraryTestBase {

  private static final String EXPECTED_OUTPUT = StringUtils.lines("SuppliedString2", "OK");

  private final TestParameters parameters;
  private final CompilationSpecification compilationSpecification;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build(),
        getJdk8Jdk11(),
        DEFAULT_SPECIFICATIONS);
  }

  public RequiredNonNullWithSupplierTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.compilationSpecification = compilationSpecification;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
  }

  @Test
  public void testRequireNonNull() throws Throwable {
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addInnerClasses(getClass())
        .addKeepMainRule(Executor.class)
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  static class Executor {

    public static void main(String[] args) {
      Object o = System.currentTimeMillis() > 10 ? new Object() : new Object();
      Objects.requireNonNull(o, () -> "SuppliedString");
      try {
        Objects.requireNonNull(null, () -> "SuppliedString2");
        throw new AssertionError("Unexpected");
      } catch (NullPointerException e) {
        System.out.println(e.getMessage());
      }
      try {
        Objects.requireNonNull(null, (Supplier<String>) null);
        throw new AssertionError("Unexpected");
      } catch (NullPointerException e) {
        // Normally we would want to print the exception message, but some ART versions have a bug
        // where they erroneously calls supplier.get() on a null reference which produces the NPE
        // but with an ART-defined message. See b/147419222.
        System.out.println("OK");
      }
    }
  }
}
